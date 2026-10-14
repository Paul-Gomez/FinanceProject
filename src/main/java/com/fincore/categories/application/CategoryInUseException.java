package com.fincore.categories.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.util.UUID;

public class CategoryInUseException extends BusinessException {

    public CategoryInUseException(UUID categoryId) {
        super(ErrorType.CONFLICT, "CATEGORY_IN_USE", "La categoría " + categoryId + " está en uso y no se puede borrar");
    }
}
