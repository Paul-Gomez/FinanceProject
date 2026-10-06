package com.fincore.categories.application;

import com.fincore.categories.domain.CategoryKind;

public class CategoryKindMismatchException extends RuntimeException {

    public CategoryKindMismatchException(String categoryName, CategoryKind actual, CategoryKind expected) {
        super("La categoría '%s' es de tipo %s y este movimiento necesita una de tipo %s"
                .formatted(categoryName, actual, expected));
    }
}
