package com.example.walletledger;

import com.example.walletledger.api.WalletDtos;
import com.example.walletledger.exception.ApiExceptions.IdempotencyConflict;
import com.example.walletledger.exception.ApiExceptions.InsufficientFunds;
import com.example.walletledger.exception.ApiExceptions.WalletNotFound;
import com.example.walletledger.repository.LedgerTransactionRepository;
import com.example.walletledger.repository.WalletRepository;
import com.example.walletledger.service.WalletService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class WalletServiceConcurrencyTest {
    @Autowired WalletService service;
    @Autowired WalletRepository wallets;
    @Autowired LedgerTransactionRepository transactions;

    @BeforeEach void setup() {
        transactions.deleteAll();
        wallets.deleteAll();
        service.createWallet("player-1");
    }

    @Test
    void concurrentCreateWalletIsIdempotent() throws Exception {
        var playerId = "player-race";
        var baseline = wallets.count();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);

        var t1 = pool.submit(() -> { start.await(); return service.createWallet(playerId); });
        var t2 = pool.submit(() -> { start.await(); return service.createWallet(playerId); });

        start.countDown();
        var r1 = t1.get(10, TimeUnit.SECONDS);
        var r2 = t2.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(r1.playerId()).isEqualTo(playerId);
        assertThat(r2.playerId()).isEqualTo(playerId);
        assertThat(r1.balance()).isEqualByComparingTo("0.00");
        assertThat(r2.balance()).isEqualByComparingTo("0.00");
        assertThat(wallets.count()).isEqualTo(baseline + 1);
    }

    @Test
    void repeatedRequestIsAppliedOnlyOnce() {
        service.credit("player-1", request("r-credit", "10.00"));
        var replay = service.credit("player-1", request("r-credit", "10.00"));
        assertThat(replay.amount()).isEqualByComparingTo("10.00");
        assertThat(service.balance("player-1").balance()).isEqualByComparingTo("10.00");
        assertThat(transactions.count()).isEqualTo(1);
    }

    @Test
    void reusedRequestIdWithDifferentPayloadIsRejected() {
        service.credit("player-1", request("same-key", "10.00"));
        assertThatThrownBy(() -> service.credit("player-1", request("same-key", "12.00")))
                .isInstanceOf(IdempotencyConflict.class);
        assertThat(service.balance("player-1").balance()).isEqualByComparingTo("10.00");
        assertThat(transactions.count()).isEqualTo(1);
    }

    @Test
    void concurrentSameRequestIsStillAppliedOnlyOnce() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        var failures = new ConcurrentLinkedQueue<Throwable>();
        var task = (Callable<WalletDtos.TransactionResponse>) () -> {
            start.await();
            try {
                return service.credit("player-1", request("shared-key", "15.00"));
            } catch (Throwable t) {
                failures.add(t);
                throw t;
            }
        };

        var f1 = pool.submit(task);
        var f2 = pool.submit(task);
        start.countDown();
        var r1 = f1.get(10, TimeUnit.SECONDS);
        var r2 = f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).isEmpty();
        assertThat(r1.requestId()).isEqualTo("shared-key");
        assertThat(r2.requestId()).isEqualTo("shared-key");
        assertThat(service.balance("player-1").balance()).isEqualByComparingTo("15.00");
        assertThat(transactions.count()).isEqualTo(1);
    }

    @Test
    void concurrentDebitsCannotOverdrawWallet() throws Exception {
        service.credit("player-1", request("seed", "100.00"));
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        var successes = new AtomicInteger();
        var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> (Callable<Void>) () -> {
            start.await();
            try { service.debit("player-1", request("debit-" + i, "80.00")); successes.incrementAndGet(); }
            catch (InsufficientFunds expected) { }
            return null;
        }).toList();
        tasks.forEach(t -> pool.submit(t)); start.countDown(); pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(successes).hasValue(1);
        assertThat(service.balance("player-1").balance()).isEqualByComparingTo("20.00");
    }

    @Test
    void failedDebitDoesNotLeavePartialUpdate() {
        service.credit("player-1", request("seed-50", "50.00"));
        assertThatThrownBy(() -> service.debit("player-1", request("overdraft", "60.00")))
                .isInstanceOf(InsufficientFunds.class);
        assertThat(service.balance("player-1").balance()).isEqualByComparingTo("50.00");
        assertThat(transactions.count()).isEqualTo(1);
    }

    @Test
    void refundRestoresDebitedAmount() {
        service.credit("player-1", request("seed-refund", "30.00"));
        service.debit("player-1", request("purchase-refund", "12.00"));

        var refund = new WalletDtos.RefundRequest("refund-1", "purchase-refund");
        var response = service.refund("player-1", refund);

        assertThat(response.requestId()).isEqualTo("refund-1");
        assertThat(response.referenceId()).isEqualTo("purchase-refund");
        assertThat(service.balance("player-1").balance()).isEqualByComparingTo("30.00");
        assertThat(transactions.count()).isEqualTo(3);
    }

    @Test
    void historyIsPaginatedAndNewestFirst() throws Exception {
        service.credit("player-1", request("h-1", "10.00"));
        Thread.sleep(5);
        service.credit("player-1", request("h-2", "20.00"));
        Thread.sleep(5);
        service.debit("player-1", request("h-3", "5.00"));

        var page0 = service.history("player-1", PageRequest.of(0, 2));
        var page1 = service.history("player-1", PageRequest.of(1, 2));

        assertThat(page0.getTotalElements()).isEqualTo(3);
        assertThat(page0.getContent()).extracting(WalletDtos.TransactionResponse::requestId)
                .containsExactly("h-3", "h-2");
        assertThat(page1.getContent()).extracting(WalletDtos.TransactionResponse::requestId)
                .containsExactly("h-1");
    }

    @Test
    void unknownWalletIsRejectedClearly() {
        assertThatThrownBy(() -> service.balance("missing"))
                .isInstanceOf(WalletNotFound.class);
        assertThatThrownBy(() -> service.history("missing", PageRequest.of(0, 10)))
                .isInstanceOf(WalletNotFound.class);
        assertThatThrownBy(() -> service.debit("missing", request("x", "1.00")))
                .isInstanceOf(WalletNotFound.class);
    }

    private WalletDtos.OperationRequest request(String id, String amount) {
        return new WalletDtos.OperationRequest(id, new BigDecimal(amount), "test", null);
    }
}
