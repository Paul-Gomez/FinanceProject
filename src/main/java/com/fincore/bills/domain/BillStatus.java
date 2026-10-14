package com.fincore.bills.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados de una factura. PAID y CANCELLED son finales: una factura pagada no vuelve a
 * pendiente (si hay que deshacer un pago, se hace con una operación explícita, no cambiando el estado).
 */
public enum BillStatus {
    PENDING,
    PAID,
    OVERDUE,
    CANCELLED;

    private static final Map<BillStatus, Set<BillStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(PAID, OVERDUE, CANCELLED),
            OVERDUE, EnumSet.of(PAID, CANCELLED),
            PAID, EnumSet.noneOf(BillStatus.class),
            CANCELLED, EnumSet.noneOf(BillStatus.class));

    public boolean canTransitionTo(BillStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public boolean isOpen() {
        return this == PENDING || this == OVERDUE;
    }
}
