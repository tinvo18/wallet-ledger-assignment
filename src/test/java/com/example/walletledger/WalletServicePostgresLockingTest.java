package com.example.walletledger;

import com.example.walletledger.api.WalletDtos;
import com.example.walletledger.exception.ApiExceptions.InsufficientFunds;
import com.example.walletledger.repository.LedgerTransactionRepository;
import com.example.walletledger.repository.WalletRepository;
import com.example.walletledger.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Tag("postgres")
class WalletServicePostgresLockingTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wallet")
            .withUsername("wallet")
            .withPassword("wallet");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired WalletService service;
    @Autowired WalletRepository wallets;
    @Autowired LedgerTransactionRepository transactions;

    @BeforeEach
    void setup() {
        transactions.deleteAll();
        wallets.deleteAll();
        service.createWallet("player-pg");
    }

    @Test
    void concurrentDebitsAreSerializedOnPostgres() throws Exception {
        service.credit("player-pg", request("seed", "100.00"));

        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        var successes = new AtomicInteger();

        var jobs = java.util.stream.IntStream.range(0, 2)
                .mapToObj(i -> (Callable<Void>) () -> {
                    start.await();
                    try {
                        service.debit("player-pg", request("pg-debit-" + i, "80.00"));
                        successes.incrementAndGet();
                    } catch (InsufficientFunds expected) {
                        // Exactly one debit should fail after the other commits.
                    }
                    return null;
                }).toList();

        jobs.forEach(pool::submit);
        start.countDown();
        pool.shutdown();

        assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        assertThat(successes).hasValue(1);
        assertThat(service.balance("player-pg").balance()).isEqualByComparingTo("20.00");
        assertThat(transactions.count()).isEqualTo(2);
    }

    private WalletDtos.OperationRequest request(String id, String amount) {
        return new WalletDtos.OperationRequest(id, new BigDecimal(amount), "test", null);
    }
}

