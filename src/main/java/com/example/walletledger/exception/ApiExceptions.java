package com.example.walletledger.exception;

public final class ApiExceptions {
    private ApiExceptions() { }

    public static class WalletNotFound extends RuntimeException {
        public WalletNotFound(String p) {
            super("Wallet not found: " + p);
        }
    }

    public static class InsufficientFunds extends RuntimeException {
        public InsufficientFunds() {
            super("Insufficient funds");
        }
    }

    public static class IdempotencyConflict extends RuntimeException {
        public IdempotencyConflict() {
            super("Request id was already used with different operation data");
        }
    }

    public static class RefundNotFound extends RuntimeException {
        public RefundNotFound(String requestId) {
            super("Original transaction not found: " + requestId);
        }
    }

    public static class InvalidRefund extends RuntimeException {
        public InvalidRefund(String message) {
            super(message);
        }
    }

    public static class TransferNotFound extends RuntimeException {
        public TransferNotFound(String playerId) {
            super("Wallet not found: " + playerId);
        }
    }

    public static class InvalidTransfer extends RuntimeException {
        public InvalidTransfer(String message) {
            super(message);
        }
    }

    public static class ClaimRewardConflict extends RuntimeException {
        public ClaimRewardConflict(String rewardId) {
            super("Reward already claimed: " + rewardId);
        }
    }

    public static class InvalidClaimReward extends RuntimeException {
        public InvalidClaimReward(String message) {
            super(message);
        }
    }
}
