package com.fincore.categories.application;

import java.util.UUID;

public class CategoryInUseException extends RuntimeException {

    public CategoryInUseException(UUID categoryId) {
        super("La categoría " + categoryId + " está en uso y no se puede borrar");
    }
}
