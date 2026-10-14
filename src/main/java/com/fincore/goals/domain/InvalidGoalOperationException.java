package com.fincore.goals.domain;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class InvalidGoalOperationException extends BusinessException {

    public InvalidGoalOperationException(String reason) {
        super(ErrorType.UNPROCESSABLE, "INVALID_GOAL_OPERATION", "Operación no válida sobre el objetivo: " + reason);
    }
}
