package com.example.walletledger.repository;

import com.example.walletledger.domain.WalletEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<WalletEntity, java.util.UUID> {

    Optional<WalletEntity> findByPlayerId(String playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletEntity w where w.playerId = :playerId")
    Optional<WalletEntity> findByPlayerIdForUpdate(@Param("playerId") String playerId);

    boolean existsByPlayerId(String playerId);
}
