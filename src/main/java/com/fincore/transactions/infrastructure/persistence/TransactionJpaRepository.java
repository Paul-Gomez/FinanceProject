package com.fincore.transactions.infrastructure.persistence;

import com.fincore.transactions.domain.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    /** Bloquea el movimiento: dos reembolsos o anulaciones a la vez del mismo movimiento van en fila. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.transferId = :transferId order by t.id")
    List<Transaction> findTransferLegsForUpdate(@Param("transferId") UUID transferId);

    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.refundedTransactionId = :expenseId and t.status = com.fincore.transactions.domain.TransactionStatus.POSTED
            """)
    BigDecimal sumPostedRefunds(@Param("expenseId") UUID expenseId);
}
