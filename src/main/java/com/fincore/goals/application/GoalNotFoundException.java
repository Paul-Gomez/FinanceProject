package com.fincore.goals.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.util.UUID;

public class GoalNotFoundException extends BusinessException {

    public GoalNotFoundException(UUID goalId) {
        super(ErrorType.NOT_FOUND, "GOAL_NOT_FOUND", "No existe ningún objetivo con id " + goalId);
    }
}
