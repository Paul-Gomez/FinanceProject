package com.fincore.budgets.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.util.UUID;

/** También si el presupuesto es de otro usuario: no se confirma que exista. */
public class BudgetNotFoundException extends BusinessException {

    public BudgetNotFoundException(UUID budgetId) {
        super(ErrorType.NOT_FOUND, "BUDGET_NOT_FOUND", "No existe ningún presupuesto con id " + budgetId);
    }
}
