package com.fincore.categories.application;

import java.util.UUID;

public class CategoryReadOnlyException extends RuntimeException {

    public CategoryReadOnlyException(UUID categoryId) {
        super("La categoría " + categoryId + " es del sistema y no se puede modificar ni borrar");
    }
}
