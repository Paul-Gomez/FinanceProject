package com.fincore.notifications.application;

import com.fincore.budgets.application.BudgetThresholdReachedEvent;
import com.fincore.categories.application.CategoryQueryService;
import com.fincore.notifications.domain.NotificationType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Convierte las alertas de presupuesto en notificaciones para el usuario. */
@Component
public class BudgetNotificationListener {

    private final NotificationService notificationService;
    private final CategoryQueryService categoryQueryService;

    public BudgetNotificationListener(NotificationService notificationService,
                                      CategoryQueryService categoryQueryService) {
        this.notificationService = notificationService;
        this.categoryQueryService = categoryQueryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BudgetThresholdReachedEvent event) {
        String category = categoryQueryService.nameOf(event.categoryId());
        boolean exceeded = event.threshold() >= 100;
        String title = exceeded
                ? "Presupuesto de %s superado".formatted(category)
                : "Has usado el %d %% del presupuesto de %s".formatted(event.threshold(), category);
        String message = "Llevas un %s %% del presupuesto de %s en el periodo que empezó el %s."
                .formatted(event.percentageUsed().toPlainString(), category, event.periodStart());

        notificationService.notify(event.ownerId(), NotificationType.BUDGET_THRESHOLD, title, message,
                "budget:%s:%s:%d".formatted(event.budgetId(), event.periodStart(), event.threshold()));
    }
}
