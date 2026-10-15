package com.fincore.transactions.infrastructure.persistence;

import com.fincore.transactions.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
}
