package com.example.walletledger.service;

import com.example.walletledger.api.WalletDtos;
import com.example.walletledger.domain.*;
import com.example.walletledger.exception.ApiExceptions.*;
import com.example.walletledger.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private final WalletRepository wallets;
    private final LedgerTransactionRepository transactions;

    public WalletService(WalletRepository wallets, LedgerTransactionRepository transactions) {
        this.wallets = wallets; this.transactions = transactions;
    }

    @Transactional
    public WalletDtos.TransactionResponse credit(String playerId, WalletDtos.OperationRequest request) {
        try {
            var response = apply(playerId, request, TransactionType.CREDIT);
            logWalletEvent("wallet.credit", playerId, request.requestId(), request.amount(), response.balanceAfter(), request.referenceId(), "success");
            return response;
        } catch (RuntimeException e) {
            logOperationFailure("wallet.credit", playerId, request.requestId(), request.amount(), request.referenceId(), e);
            throw e;
        }
    }

    public WalletDtos.WalletResponse createWallet(String playerId) {
        var existing = wallets.findByPlayerId(playerId);
        if (existing.isPresent()) {
            return new WalletDtos.WalletResponse(playerId, existing.get().getBalance());
        }
        try {
            wallets.saveAndFlush(new WalletEntity(playerId));
        } catch (DataIntegrityViolationException ignored) {
            // Another concurrent request created the same wallet first.
        }
        return new WalletDtos.WalletResponse(playerId, BigDecimal.ZERO);
    }

    @Transactional
    public WalletDtos.TransactionResponse debit(String playerId, WalletDtos.OperationRequest request) {
        try {
            var response = apply(playerId, request, TransactionType.DEBIT);
            logWalletEvent("wallet.debit", playerId, request.requestId(), request.amount(), response.balanceAfter(), request.referenceId(), "success");
            return response;
        } catch (RuntimeException e) {
            logOperationFailure("wallet.debit", playerId, request.requestId(), request.amount(), request.referenceId(), e);
            throw e;
        }
    }

    @Transactional
    public WalletDtos.TransactionResponse refund(String playerId, WalletDtos.RefundRequest request) {
        var original = transactions.findByRequestId(request.originalRequestId())
                .orElseThrow(() -> new RefundNotFound(request.originalRequestId()));
        if (!original.getPlayerId().equals(playerId)) {
            throw new InvalidRefund("Original transaction does not belong to this wallet");
        }
        if (original.getType() != TransactionType.DEBIT) {
            throw new InvalidRefund("Only debit transactions can be refunded");
        }

        var refundRequest = new WalletDtos.OperationRequest(
                request.requestId(),
                original.getAmount(),
                "refund: " + original.getRequestId(),
                original.getRequestId());
        try {
            var response = apply(playerId, refundRequest, TransactionType.CREDIT);
            logWalletEvent("wallet.refund", playerId, request.requestId(), original.getAmount(), response.balanceAfter(), original.getRequestId(), "success");
            return response;
        } catch (RuntimeException e) {
            logOperationFailure("wallet.refund", playerId, request.requestId(), original.getAmount(), original.getRequestId(), e);
            throw e;
        }
    }

    @Transactional
    public WalletDtos.TransferResponse transfer(String fromPlayerId, WalletDtos.TransferRequest request) {
        if (fromPlayerId.equals(request.toPlayerId())) {
            throw new InvalidTransfer("Transfer destination must be different from source");
        }

        var source = wallets.findByPlayerIdForUpdate(fromPlayerId)
                .orElseThrow(() -> new TransferNotFound(fromPlayerId));
        var target = wallets.findByPlayerIdForUpdate(request.toPlayerId())
                .orElseThrow(() -> new TransferNotFound(request.toPlayerId()));

        var previous = transactions.findByRequestId(request.requestId());
        if (previous.isPresent()) {
            throw new IdempotencyConflict();
        }

        var sourceNext = source.getBalance().subtract(request.amount());
        if (sourceNext.signum() < 0) {
            throw new InsufficientFunds();
        }
        var targetNext = target.getBalance().add(request.amount());

        source.changeBalance(sourceNext);
        target.changeBalance(targetNext);

        try {
            var debit = transactions.saveAndFlush(new LedgerTransactionEntity(
                    request.requestId() + ":debit",
                    fromPlayerId,
                    TransactionType.DEBIT,
                    request.amount(),
                    sourceNext,
                    request.reason(),
                    request.toPlayerId() + ":" + request.referenceId()));
            var credit = transactions.saveAndFlush(new LedgerTransactionEntity(
                    request.requestId() + ":credit",
                    request.toPlayerId(),
                    TransactionType.CREDIT,
                    request.amount(),
                    targetNext,
                    request.reason(),
                    fromPlayerId + ":" + request.referenceId()));
            return new WalletDtos.TransferResponse(
                    WalletDtos.TransactionResponse.from(debit),
                    WalletDtos.TransactionResponse.from(credit));
        } catch (DataIntegrityViolationException ex) {
            logOperationFailure("wallet.transfer", fromPlayerId, request.requestId(), request.amount(), request.toPlayerId(), ex);
            throw new IdempotencyConflict();
        }
    }

    @Transactional
    public WalletDtos.TransactionResponse claimReward(String playerId, WalletDtos.ClaimRewardRequest request) {
        if (request.amount().signum() <= 0) {
            throw new InvalidClaimReward("Reward amount must be positive");
        }
        if (request.rewardId().isBlank()) {
            throw new InvalidClaimReward("Reward id is required");
        }

        var previous = transactions.findByRequestId(request.requestId());
        if (previous.isPresent()) {
            var old = previous.get();
            if (!old.getPlayerId().equals(playerId)
                    || old.getType() != TransactionType.CREDIT
                    || old.getAmount().compareTo(request.amount()) != 0
                    || !old.getReason().equals(request.reason())
                    || !java.util.Objects.equals(old.getReferenceId(), request.rewardId())) {
                throw new IdempotencyConflict();
            }
            return WalletDtos.TransactionResponse.from(old);
        }

        var wallet = wallets.findByPlayerIdForUpdate(playerId)
                .orElseThrow(() -> new WalletNotFound(playerId));
        previous = transactions.findByRequestId(request.requestId());
        if (previous.isPresent()) {
            throw new IdempotencyConflict();
        }

        var next = wallet.getBalance().add(request.amount());
        wallet.changeBalance(next);
        try {
            var ledger = transactions.saveAndFlush(new LedgerTransactionEntity(
                    request.requestId(),
                    playerId,
                    TransactionType.CREDIT,
                    request.amount(),
                    next,
                    request.reason(),
                    request.rewardId()));
            return WalletDtos.TransactionResponse.from(ledger);
        } catch (DataIntegrityViolationException ex) {
            logOperationFailure("wallet.claim", playerId, request.requestId(), request.amount(), request.rewardId(), ex);
            throw new ClaimRewardConflict(request.rewardId());
        }
    }

    private WalletDtos.TransactionResponse apply(String playerId, WalletDtos.OperationRequest request, TransactionType type) {
        var previous = transactions.findByRequestId(request.requestId());
        if (previous.isPresent()) {
            var old = previous.get();
            if (!sameOperation(old, playerId, request, type)) {
                throw new IdempotencyConflict();
            }
            return WalletDtos.TransactionResponse.from(old);
        }

        // The row lock serialises all balance changes for one player.
        var wallet = wallets.findByPlayerIdForUpdate(playerId).orElseThrow(() -> new WalletNotFound(playerId));
        // Re-check after taking the lock to handle same-key concurrent requests for one player.
        previous = transactions.findByRequestId(request.requestId());
        if (previous.isPresent()) {
            var old = previous.get();
            if (!sameOperation(old, playerId, request, type)) throw new IdempotencyConflict();
            return WalletDtos.TransactionResponse.from(old);
        }

        var current = wallet.getBalance();
        var next = type == TransactionType.CREDIT ? current.add(request.amount()) : current.subtract(request.amount());
        // Reject debit if the balance would become negative (insufficient funds)
        if (next.signum() < 0) throw new InsufficientFunds();

        wallet.changeBalance(next);
        try {
           /**
            * Persist the transaction to ledger with all details: request ID, player, type, amount, new balance, reason, and reference
            * If unique constraint on requestId is violated, it means this exact request was already processed
            * */
            var ledger = transactions.saveAndFlush(
                    new LedgerTransactionEntity(request.requestId(),
                    playerId, type,
                    request.amount(),
                    next,
                    request.reason(),
                    request.referenceId()));
            return WalletDtos.TransactionResponse.from(ledger);
        } catch (DataIntegrityViolationException ex) {
            throw new IdempotencyConflict();
        }
    }

    private void logWalletEvent(String event, String playerId, String requestId, BigDecimal amount, BigDecimal balanceAfter,
                                String referenceId, String result) {
        log.info("event={} playerId={} requestId={} amount={} balanceAfter={} referenceId={} result={}",
                event, playerId, requestId, amount, balanceAfter, referenceId, result);
    }

    private void logOperationFailure(String event, String playerId, String requestId, BigDecimal amount, String referenceId, Exception e) {
        log.info("event={} playerId={} requestId={} amount={} referenceId={} result=failure:{}",
                event, playerId, requestId, amount, referenceId, e.getClass().getSimpleName());
    }

    /**
     * Checks if a previous transaction is the same as the current request (for idempotency).
     * Returns true if all details match, ensuring the retry is safe to skip.
     **/
    private boolean sameOperation(LedgerTransactionEntity old, String playerId,
                                  WalletDtos.OperationRequest request, TransactionType type) {
        return old.getPlayerId().equals(playerId)
                && old.getType() == type
                && old.getAmount().compareTo(request.amount()) == 0
                && old.getReason().equals(request.reason())
                && java.util.Objects.equals(old.getReferenceId(), request.referenceId());
    }

    @Transactional(readOnly = true)
    public WalletDtos.WalletResponse balance(String playerId) {
        var wallet = wallets
                .findByPlayerId(playerId)
                .orElseThrow(() -> new WalletNotFound(playerId));
        return new WalletDtos.WalletResponse(playerId, wallet.getBalance());
    }

    @Transactional(readOnly = true)
    public Page<WalletDtos.TransactionResponse> history(String playerId, Pageable pageable) {
        if (!wallets.existsByPlayerId(playerId)) throw new WalletNotFound(playerId);
        return transactions
                .findByPlayerIdOrderByCreatedAtDesc(playerId, pageable)
                .map(WalletDtos.TransactionResponse::from);
    }
}
