package com.fincore.transactions.infrastructure.persistence;

import com.fincore.transactions.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findAllByAccountId(UUID accountId, Pageable pageable);
}
