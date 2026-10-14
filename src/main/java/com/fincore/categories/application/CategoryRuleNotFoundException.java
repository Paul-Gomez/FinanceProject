package com.fincore.categories.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.util.UUID;

public class CategoryRuleNotFoundException extends BusinessException {

    public CategoryRuleNotFoundException(UUID ruleId) {
        super(ErrorType.NOT_FOUND, "CATEGORY_RULE_NOT_FOUND", "No existe ninguna regla con id " + ruleId);
    }
}
