package com.fincore.categories.application;

public class CategoryAlreadyExistsException extends RuntimeException {

    public CategoryAlreadyExistsException(String name) {
        super("Ya existe una categoría llamada '%s' de ese tipo".formatted(name));
    }
}
