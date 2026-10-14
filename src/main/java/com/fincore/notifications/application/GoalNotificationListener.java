package com.fincore.notifications.application;

import com.fincore.goals.application.GoalAchievedEvent;
import com.fincore.notifications.domain.NotificationType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class GoalNotificationListener {

    private final NotificationService notificationService;

    public GoalNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(GoalAchievedEvent event) {
        notificationService.notify(event.ownerId(), NotificationType.GOAL_ACHIEVED,
                "¡Objetivo conseguido: %s!".formatted(event.goalName()),
                "Has alcanzado el importe de tu objetivo de ahorro \"%s\".".formatted(event.goalName()), null);
    }
}
