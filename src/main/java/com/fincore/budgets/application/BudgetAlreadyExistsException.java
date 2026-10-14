package com.fincore.budgets.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class BudgetAlreadyExistsException extends BusinessException {

    public BudgetAlreadyExistsException() {
        super(ErrorType.CONFLICT, "BUDGET_ALREADY_EXISTS", "Ya tienes un presupuesto activo para esa categoría, periodo y moneda");
    }
}
