package com.fincore.shared.observability;

import com.fincore.shared.audit.AuditEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Métricas de negocio a partir de los eventos de auditoría: cuántos logins, logins fallidos,
 * movimientos, transferencias, importaciones... hay por unidad de tiempo. Solo se cuentan las
 * operaciones confirmadas (AFTER_COMMIT), salvo las "immediate" como el login fallido.
 * Las métricas HTTP (peticiones, errores, latencia) ya las da Spring Boot (http.server.requests).
 */
@Component
public class BusinessMetrics {

    static final String EVENTS = "fincore.business.events";

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void count(AuditEvent event) {
        if (!event.immediate()) {
            increment(event);
        }
    }

    @org.springframework.context.event.EventListener(condition = "#event.immediate()")
    public void countImmediate(AuditEvent event) {
        increment(event);
    }

    private void increment(AuditEvent event) {
        Counter.builder(EVENTS)
                .description("Acciones de negocio confirmadas, por tipo")
                .tag("action", event.action().name())
                .register(registry)
                .increment();
    }
}
