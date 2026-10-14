package com.fincore.budgets.application;

import com.fincore.budgets.domain.Budget;
import com.fincore.budgets.domain.BudgetPeriod;
import com.fincore.budgets.domain.BudgetStatus;
import com.fincore.budgets.infrastructure.persistence.BudgetRepository;
import com.fincore.categories.application.CategoryQueryService;
import com.fincore.categories.domain.CategoryKind;
import com.fincore.shared.money.Money;
import com.fincore.shared.time.DateRange;
import com.fincore.transactions.application.CategorySpending;
import com.fincore.transactions.application.SpendingQueryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryQueryService categoryQueryService;
    private final SpendingQueryService spendingQueryService;

    public BudgetService(BudgetRepository budgetRepository, CategoryQueryService categoryQueryService,
                         SpendingQueryService spendingQueryService) {
        this.budgetRepository = budgetRepository;
        this.categoryQueryService = categoryQueryService;
        this.spendingQueryService = spendingQueryService;
    }

    public record BudgetView(Budget budget, DateRange period, BudgetStatus status) {
    }

    @Transactional
    public Budget create(UUID ownerId, UUID categoryId, BigDecimal amount, String currencyCode, BudgetPeriod period) {
        categoryQueryService.requireUsable(categoryId, ownerId, CategoryKind.EXPENSE);
        Money limit = Money.ofExact(amount, Money.currencyFor(currencyCode));
        try {
            return budgetRepository.saveAndFlush(new Budget(ownerId, categoryId, limit, period));
        } catch (DataIntegrityViolationException e) {
            throw new BudgetAlreadyExistsException();
        }
    }

    @Transactional
    public Budget changeAmount(UUID budgetId, UUID ownerId, BigDecimal amount) {
        Budget budget = getOwned(budgetId, ownerId);
        budget.changeAmount(Money.ofExact(amount, Money.currencyFor(budget.getCurrencyCode())));
        return budget;
    }

    @Transactional
    public void deactivate(UUID budgetId, UUID ownerId) {
        getOwned(budgetId, ownerId).deactivate();
    }

    @Transactional(readOnly = true)
    public BudgetView getStatus(UUID budgetId, UUID ownerId, LocalDate date) {
        Budget budget = getOwned(budgetId, ownerId);
        return evaluate(ownerId, List.of(budget), date).get(0);
    }

    /** Situación de todos los presupuestos activos del usuario en el periodo que contiene la fecha. */
    @Transactional(readOnly = true)
    public List<BudgetView> listStatus(UUID ownerId, LocalDate date) {
        return evaluate(ownerId, budgetRepository.findAllByOwnerIdAndActiveTrue(ownerId), date);
    }

    /**
     * Una consulta de gasto (agrupada por categoría y moneda) por cada periodo distinto, no una por
     * presupuesto: con 15 presupuestos mensuales se hace 1 consulta, no 15. Las subcategorías se
     * resuelven con otra única consulta y se suman al presupuesto de su categoría padre.
     */
    private List<BudgetView> evaluate(UUID ownerId, List<Budget> budgets, LocalDate date) {
        if (budgets.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<UUID>> childrenByParent = categoryQueryService.childrenByParent(ownerId);
        Map<DateRange, Map<String, BigDecimal>> spendingByPeriod = new HashMap<>();
        List<BudgetView> views = new ArrayList<>();

        for (Budget budget : budgets) {
            DateRange range = budget.getPeriod().rangeContaining(date);
            Map<String, BigDecimal> spending = spendingByPeriod.computeIfAbsent(range,
                    r -> indexByCategoryAndCurrency(spendingQueryService.expensesByCategory(ownerId, r)));

            BigDecimal spent = BigDecimal.ZERO;
            List<UUID> categories = new ArrayList<>(childrenByParent.getOrDefault(budget.getCategoryId(), List.of()));
            categories.add(budget.getCategoryId());
            for (UUID categoryId : categories) {
                spent = spent.add(spending.getOrDefault(key(categoryId, budget.getCurrencyCode()), BigDecimal.ZERO));
            }

            Money budgeted = budget.amount();
            views.add(new BudgetView(budget, range, BudgetStatus.of(budgeted, Money.of(spent, budgeted.currency()))));
        }
        return views;
    }

    private static Map<String, BigDecimal> indexByCategoryAndCurrency(List<CategorySpending> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> key(row.categoryId(), row.currencyCode()), CategorySpending::total, BigDecimal::add));
    }

    private static String key(UUID categoryId, String currencyCode) {
        return categoryId + "|" + currencyCode;
    }

    private Budget getOwned(UUID budgetId, UUID ownerId) {
        return budgetRepository.findById(budgetId)
                .filter(budget -> budget.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new BudgetNotFoundException(budgetId));
    }
}
