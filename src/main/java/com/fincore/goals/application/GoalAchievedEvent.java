package com.fincore.goals.application;

import java.util.UUID;

/** Se publica cuando un objetivo pasa a estar conseguido. */
public record GoalAchievedEvent(UUID ownerId, UUID goalId, String goalName) {
}
