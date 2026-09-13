package com.example.walletledger.repository;

import com.example.walletledger.domain.LedgerTransactionEntity;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionEntity, UUID> {
    Optional<LedgerTransactionEntity> findByRequestId(String requestId);
    Page<LedgerTransactionEntity> findByPlayerIdOrderByCreatedAtDesc(String playerId, Pageable pageable);
}
