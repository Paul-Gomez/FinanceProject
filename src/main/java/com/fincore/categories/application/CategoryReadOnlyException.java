package com.fincore.categories.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.util.UUID;

public class CategoryReadOnlyException extends BusinessException {

    public CategoryReadOnlyException(UUID categoryId) {
        super(ErrorType.FORBIDDEN, "CATEGORY_READ_ONLY", "La categoría " + categoryId + " es del sistema y no se puede modificar ni borrar");
    }
}
