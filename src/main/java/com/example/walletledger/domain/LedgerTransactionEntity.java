package com.example.walletledger.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_transactions", indexes = @Index(name = "idx_ledger_player_created", columnList = "player_id, created_at"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerTransactionEntity {
    @Id private UUID id;

    @Column(name = "request_id", nullable = false, unique = true, length = 100)
    private String requestId;

    @Column(name = "player_id", nullable = false, length = 100)
    private String playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public LedgerTransactionEntity(String requestId, String playerId, TransactionType type, BigDecimal amount,
                                   BigDecimal balanceAfter, String reason, String referenceId) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.playerId = playerId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.reason = reason;
        this.referenceId = referenceId;
        this.createdAt = Instant.now();
    }
}
