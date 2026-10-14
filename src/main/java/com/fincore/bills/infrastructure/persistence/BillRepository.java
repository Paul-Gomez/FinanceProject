package com.fincore.bills.infrastructure.persistence;

import com.fincore.bills.domain.Bill;
import com.fincore.bills.domain.BillStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillRepository extends JpaRepository<Bill, UUID> {

    List<Bill> findAllByOwnerIdOrderByDueDateAsc(UUID ownerId);

    List<Bill> findAllByOwnerIdAndStatusOrderByDueDateAsc(UUID ownerId, BillStatus status);

    /** Con bloqueo: dos pagos simultáneos de la misma factura no pueden crear dos gastos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bill b where b.id = :id")
    Optional<Bill> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select b from Bill b
            where b.status = com.fincore.bills.domain.BillStatus.PENDING
              and b.dueDate between :from and :to
            """)
    List<Bill> findPendingDueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select b from Bill b
            where b.ownerId = :ownerId and b.status in (com.fincore.bills.domain.BillStatus.PENDING,
                                                         com.fincore.bills.domain.BillStatus.OVERDUE)
              and b.dueDate <= :to
            order by b.dueDate
            """)
    List<Bill> findOpenDueUntil(@Param("ownerId") UUID ownerId, @Param("to") LocalDate to);
}
