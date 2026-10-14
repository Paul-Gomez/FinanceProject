package com.fincore.categories.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;
import com.fincore.categories.domain.CategoryKind;

public class CategoryKindMismatchException extends BusinessException {

    public CategoryKindMismatchException(String categoryName, CategoryKind actual, CategoryKind expected) {
        super(ErrorType.UNPROCESSABLE, "CATEGORY_KIND_MISMATCH", "La categoría '%s' es de tipo %s y este movimiento necesita una de tipo %s"
                .formatted(categoryName, actual, expected));
    }
}
