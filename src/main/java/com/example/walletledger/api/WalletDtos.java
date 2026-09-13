package com.example.walletledger.api;

import com.example.walletledger.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class WalletDtos {
    private WalletDtos() { }

    public record OperationRequest(@NotBlank @Size(max = 100) String requestId,
                                   @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
                                   @NotBlank @Size(max = 255) String reason,
                                   @Size(max = 100) String referenceId) { }

    public record RefundRequest(@NotBlank @Size(max = 100) String requestId,
                                @NotBlank @Size(max = 100) String originalRequestId) { }

    public record TransferRequest(@NotBlank @Size(max = 100) String requestId,
                                  @NotBlank @Size(max = 100) String toPlayerId,
                                  @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
                                  @NotBlank @Size(max = 255) String reason,
                                  @Size(max = 100) String referenceId) { }

    public record TransferResponse(TransactionResponse debit, TransactionResponse credit) { }

    public record ClaimRewardRequest(@NotBlank @Size(max = 100) String requestId,
                                     @NotBlank @Size(max = 100) String rewardId,
                                     @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
                                     @NotBlank @Size(max = 255) String reason,
                                     @Size(max = 100) String referenceId) { }

    public record WalletResponse(String playerId, BigDecimal balance) { }

    public record TransactionResponse(UUID id, String requestId, String playerId, TransactionType type,
                                      BigDecimal amount, BigDecimal balanceAfter, String reason,
                                      String referenceId, Instant createdAt) {
        public static TransactionResponse from(LedgerTransactionEntity e) {
            return new TransactionResponse(e.getId(), e.getRequestId(), e.getPlayerId(), e.getType(), e.getAmount(),
                    e.getBalanceAfter(), e.getReason(), e.getReferenceId(), e.getCreatedAt());
        }
    }
}
