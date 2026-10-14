package com.fincore.budgets.application;

public class BudgetAlreadyExistsException extends RuntimeException {

    public BudgetAlreadyExistsException() {
        super("Ya tienes un presupuesto activo para esa categoría, periodo y moneda");
    }
}
