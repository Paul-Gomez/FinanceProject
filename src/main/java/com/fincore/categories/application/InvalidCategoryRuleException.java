package com.fincore.categories.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class InvalidCategoryRuleException extends BusinessException {

    public InvalidCategoryRuleException(String reason) {
        super(ErrorType.UNPROCESSABLE, "INVALID_CATEGORY_RULE", "Regla no válida: " + reason);
    }
}
