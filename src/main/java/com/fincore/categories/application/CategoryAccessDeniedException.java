package com.fincore.categories.application;

import java.util.UUID;

public class CategoryAccessDeniedException extends RuntimeException {

    public CategoryAccessDeniedException(UUID categoryId) {
        super("No tienes permiso para acceder a la categoría " + categoryId);
    }
}
