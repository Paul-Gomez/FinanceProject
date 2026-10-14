package com.fincore.budgets.application;

import java.util.UUID;

/** También si el presupuesto es de otro usuario: no se confirma que exista. */
public class BudgetNotFoundException extends RuntimeException {

    public BudgetNotFoundException(UUID budgetId) {
        super("No existe ningún presupuesto con id " + budgetId);
    }
}
