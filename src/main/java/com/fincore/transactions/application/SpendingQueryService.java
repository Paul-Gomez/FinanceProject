package com.fincore.transactions.application;

import com.fincore.shared.time.DateRange;
import com.fincore.transactions.infrastructure.persistence.SpendingQueryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** API pública de lectura de gastos para otros módulos (presupuestos, analítica). */
@Service
public class SpendingQueryService {

    private final SpendingQueryRepository repository;

    public SpendingQueryService(SpendingQueryRepository repository) {
        this.repository = repository;
    }

    public List<CategorySpending> expensesByCategory(UUID ownerId, DateRange range) {
        return repository.expensesByCategory(ownerId, range);
    }
}
