package com.fincore.jobs;

import com.fincore.bills.application.BillService;
import com.fincore.bills.domain.Bill;
import com.fincore.identity.application.IdentityMaintenanceService;
import com.fincore.notifications.application.NotificationService;
import com.fincore.notifications.domain.NotificationType;
import com.fincore.shared.idempotency.IdempotencyService;
import com.fincore.subscriptions.application.SubscriptionService;
import com.fincore.subscriptions.application.SubscriptionService.UpcomingCharge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.function.IntSupplier;

/**
 * Tareas programadas. Todas son idempotentes (ejecutarlas dos veces no duplica nada: los avisos
 * llevan clave de deduplicación y los UPDATE solo tocan lo que todavía no estaba hecho), así que
 * no necesitan Quartz ni guardar estado. Con varias instancias de la aplicación habría que evitar
 * que corran en todas a la vez (por ejemplo con ShedLock).
 * Los métodos run* son públicos para poder lanzarlos desde los tests sin esperar al cron.
 */
@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final BillService billService;
    private final SubscriptionService subscriptionService;
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final IdentityMaintenanceService identityMaintenance;
    private final Clock clock;
    private final int reminderDays;

    public ScheduledJobs(BillService billService, SubscriptionService subscriptionService,
                         NotificationService notificationService, IdempotencyService idempotencyService,
                         IdentityMaintenanceService identityMaintenance, Clock clock,
                         @Value("${fincore.jobs.reminder-days:3}") int reminderDays) {
        this.billService = billService;
        this.subscriptionService = subscriptionService;
        this.notificationService = notificationService;
        this.idempotencyService = idempotencyService;
        this.identityMaintenance = identityMaintenance;
        this.clock = clock;
        this.reminderDays = reminderDays;
    }

    @Scheduled(cron = "${fincore.jobs.overdue-bills-cron:0 5 0 * * *}", zone = "UTC")
    public void overdueBills() {
        run("facturas vencidas", this::runOverdueBills);
    }

    @Scheduled(cron = "${fincore.jobs.reminders-cron:0 0 8 * * *}", zone = "UTC")
    public void reminders() {
        run("recordatorios de facturas", this::runBillReminders);
        run("recordatorios de suscripciones", this::runSubscriptionReminders);
    }

    @Scheduled(cron = "${fincore.jobs.cleanup-cron:0 30 * * * *}", zone = "UTC")
    public void cleanup() {
        run("limpieza de claves de idempotencia", idempotencyService::deleteExpired);
        run("limpieza de sesiones", identityMaintenance::deleteOldSessions);
        run("limpieza de tokens de un solo uso", identityMaintenance::deleteOldTokens);
    }

    public int runOverdueBills() {
        int notified = 0;
        for (var bill : billService.markOverdueBills()) {
            if (notificationService.notify(bill.ownerId(), NotificationType.BILL_OVERDUE,
                    "Factura vencida: %s".formatted(bill.provider()),
                    "La factura de %s venció el %s y sigue sin pagar.".formatted(bill.provider(), bill.dueDate()),
                    "bill:%s:overdue".formatted(bill.id()))) {
                notified++;
            }
        }
        return notified;
    }

    public int runBillReminders() {
        int notified = 0;
        for (Bill bill : billService.pendingDueWithin(reminderDays)) {
            if (notificationService.notify(bill.getOwnerId(), NotificationType.BILL_DUE_SOON,
                    "La factura de %s vence pronto".formatted(bill.getProvider()),
                    "Vence el %s: %s.".formatted(bill.getDueDate(), bill.amount()),
                    "bill:%s:due-soon".formatted(bill.getId()))) {
                notified++;
            }
        }
        return notified;
    }

    public int runSubscriptionReminders() {
        LocalDate today = LocalDate.now(clock);
        int notified = 0;
        for (UpcomingCharge charge : subscriptionService.upcomingForEveryone(today, today.plusDays(reminderDays))) {
            if (notificationService.notify(charge.ownerId(), NotificationType.SUBSCRIPTION_UPCOMING,
                    "Próximo cobro de %s".formatted(charge.name()),
                    "El %s se cobrarán %s.".formatted(charge.date(), charge.amount()),
                    "subscription:%s:%s".formatted(charge.subscriptionId(), charge.date()))) {
                notified++;
            }
        }
        return notified;
    }

    private void run(String name, IntSupplier job) {
        long start = System.nanoTime();
        try {
            int affected = job.getAsInt();
            log.info("Job '{}' terminado: {} elementos en {} ms", name, affected, (System.nanoTime() - start) / 1_000_000);
        } catch (RuntimeException e) {
            // Un job que falla no debe impedir que se ejecuten los demás ni la siguiente ejecución.
            log.error("Job '{}' ha fallado", name, e);
        }
    }
}
