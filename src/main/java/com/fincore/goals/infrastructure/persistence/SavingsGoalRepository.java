package com.fincore.goals.infrastructure.persistence;

import com.fincore.goals.domain.SavingsGoal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    List<SavingsGoal> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    /** Con bloqueo: dos aportaciones a la vez no pueden pisarse el saldo ahorrado. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from SavingsGoal g where g.id = :id")
    Optional<SavingsGoal> findByIdForUpdate(@Param("id") UUID id);
}
