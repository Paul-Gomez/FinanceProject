package com.fincore.categories.application;

public class InvalidCategoryParentException extends RuntimeException {

    public InvalidCategoryParentException(String reason) {
        super("Categoría padre no válida: " + reason);
    }
}
