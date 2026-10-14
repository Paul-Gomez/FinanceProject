package com.fincore.categories.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.util.UUID;

public class CategoryNotFoundException extends BusinessException {

    public CategoryNotFoundException(UUID categoryId) {
        super(ErrorType.NOT_FOUND, "CATEGORY_NOT_FOUND", "No existe ninguna categoría con id " + categoryId);
    }
}
