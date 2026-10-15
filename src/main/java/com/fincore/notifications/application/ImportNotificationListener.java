package com.fincore.notifications.application;

import com.fincore.imports.application.ImportCompletedEvent;
import com.fincore.notifications.domain.NotificationType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ImportNotificationListener {

    private final NotificationService notificationService;

    public ImportNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ImportCompletedEvent event) {
        notificationService.notify(event.ownerId(), NotificationType.IMPORT_COMPLETED, "Importación completada",
                "%d movimientos importados, %d duplicados y %d con errores.".formatted(
                        event.imported(), event.duplicates(), event.errors()),
                "import:" + event.importJobId());
    }
}
