package com.fincore.budgets.infrastructure.web;

import com.fincore.budgets.application.BudgetService;
import com.fincore.budgets.infrastructure.web.dto.BudgetResponse;
import com.fincore.budgets.infrastructure.web.dto.CreateBudgetRequest;
import com.fincore.budgets.infrastructure.web.dto.UpdateBudgetRequest;
import com.fincore.identity.infrastructure.security.CurrentUserId;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;
    private final Clock clock;

    public BudgetController(BudgetService budgetService, Clock clock) {
        this.budgetService = budgetService;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(@CurrentUserId UUID userId,
                                                 @Valid @RequestBody CreateBudgetRequest request) {
        var budget = budgetService.create(userId, request.categoryId(), request.amount(),
                request.currencyCode(), request.period());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(budgetService.getStatus(budget.getId(), userId, today())));
    }

    /** Situación de los presupuestos en el periodo que contiene la fecha (por defecto, hoy). */
    @GetMapping
    public List<BudgetResponse> list(@CurrentUserId UUID userId,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return budgetService.listStatus(userId, date != null ? date : today()).stream()
                .map(BudgetController::toResponse)
                .toList();
    }

    @GetMapping("/{budgetId}")
    public BudgetResponse get(@CurrentUserId UUID userId, @PathVariable UUID budgetId,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return toResponse(budgetService.getStatus(budgetId, userId, date != null ? date : today()));
    }

    @PutMapping("/{budgetId}")
    public BudgetResponse update(@CurrentUserId UUID userId, @PathVariable UUID budgetId,
                                 @Valid @RequestBody UpdateBudgetRequest request) {
        budgetService.changeAmount(budgetId, userId, request.amount());
        return toResponse(budgetService.getStatus(budgetId, userId, today()));
    }

    @DeleteMapping("/{budgetId}")
    public ResponseEntity<Void> delete(@CurrentUserId UUID userId, @PathVariable UUID budgetId) {
        budgetService.deactivate(budgetId, userId);
        return ResponseEntity.noContent().build();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static BudgetResponse toResponse(BudgetService.BudgetView view) {
        var status = view.status();
        return new BudgetResponse(view.budget().getId(), view.budget().getCategoryId(), view.budget().getPeriod(),
                view.budget().getCurrencyCode(), view.period().from(), view.period().to(),
                status.budgeted().amount(), status.spent().amount(), status.remaining().amount(),
                status.percentageUsed(), status.exceeded(), status.excess().amount());
    }
}
