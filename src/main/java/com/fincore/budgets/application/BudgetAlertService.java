package com.fincore.budgets.application;

import com.fincore.budgets.infrastructure.persistence.BudgetAlertRepository;
import com.fincore.transactions.application.TransactionRecordedEvent;
import com.fincore.transactions.domain.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Después de cada gasto confirmado revisa si algún presupuesto ha llegado al 80 % o al 100 %.
 * Va después del commit y en su propia transacción: el gasto ya está guardado pase lo que pase
 * aquí, y un fallo al calcular alertas no puede deshacerlo.
 */
@Service
public class BudgetAlertService {

    static final List<Integer> THRESHOLDS = List.of(80, 100);
    private static final Logger log = LoggerFactory.getLogger(BudgetAlertService.class);

    private final BudgetService budgetService;
    private final BudgetAlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BudgetAlertService(BudgetService budgetService, BudgetAlertRepository alertRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.budgetService = budgetService;
        this.alertRepository = alertRepository;
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTransactionRecorded(TransactionRecordedEvent event) {
        if (event.type() != TransactionType.EXPENSE || event.categoryId() == null) {
            return;
        }
        try {
            checkThresholds(event.ownerId(), event.date());
        } catch (RuntimeException e) {
            log.error("No se pudieron revisar las alertas de presupuesto del usuario {}", event.ownerId(), e);
        }
    }

    /** También lo usa el job diario, por si algún gasto entró por un camino que no publica eventos. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkThresholds(UUID ownerId, LocalDate date) {
        for (BudgetService.BudgetView view : budgetService.listStatus(ownerId, date)) {
            for (int threshold : THRESHOLDS) {
                if (view.status().reached(threshold)
                        && alertRepository.registerIfFirst(view.budget().getId(), view.period().from(), threshold)) {
                    eventPublisher.publishEvent(new BudgetThresholdReachedEvent(ownerId, view.budget().getId(),
                            view.budget().getCategoryId(), threshold, view.period().from(),
                            view.status().percentageUsed()));
                }
            }
        }
    }
}
