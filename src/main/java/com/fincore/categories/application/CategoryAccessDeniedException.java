package com.fincore.categories.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.util.UUID;

public class CategoryAccessDeniedException extends BusinessException {

    public CategoryAccessDeniedException(UUID categoryId) {
        super(ErrorType.FORBIDDEN, "ACCESS_DENIED", "No tienes permiso para acceder a la categoría " + categoryId);
    }
}
