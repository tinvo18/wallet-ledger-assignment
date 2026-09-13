package com.example.walletledger.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WalletEntity {
    @Id private UUID id;

    @Column(name = "player_id", nullable = false, unique = true, length = 100)
    private String playerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WalletEntity(String playerId) {
        this.id = UUID.randomUUID();
        this.playerId = playerId;
        this.balance = BigDecimal.ZERO;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void changeBalance(BigDecimal newBalance) {
        this.balance = newBalance;
        this.updatedAt = Instant.now();
    }
}
