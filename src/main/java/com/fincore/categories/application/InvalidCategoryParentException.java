package com.fincore.categories.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class InvalidCategoryParentException extends BusinessException {

    public InvalidCategoryParentException(String reason) {
        super(ErrorType.UNPROCESSABLE, "INVALID_CATEGORY_PARENT", "Categoría padre no válida: " + reason);
    }
}
