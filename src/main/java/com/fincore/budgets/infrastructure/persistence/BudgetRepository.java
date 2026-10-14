package com.fincore.budgets.infrastructure.persistence;

import com.fincore.budgets.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findAllByOwnerIdAndActiveTrue(UUID ownerId);
}
