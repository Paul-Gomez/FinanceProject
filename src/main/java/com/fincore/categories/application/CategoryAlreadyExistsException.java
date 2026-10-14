package com.fincore.categories.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class CategoryAlreadyExistsException extends BusinessException {

    public CategoryAlreadyExistsException(String name) {
        super(ErrorType.CONFLICT, "CATEGORY_ALREADY_EXISTS", "Ya existe una categoría llamada '%s' de ese tipo".formatted(name));
    }
}
