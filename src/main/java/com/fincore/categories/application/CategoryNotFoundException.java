package com.fincore.categories.application;

import java.util.UUID;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(UUID categoryId) {
        super("No existe ninguna categoría con id " + categoryId);
    }
}
