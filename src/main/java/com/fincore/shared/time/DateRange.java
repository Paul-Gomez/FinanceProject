package com.fincore.shared.time;

import java.time.LocalDate;

/** Rango de fechas con ambos extremos incluidos. */
public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("El rango termina antes de empezar: " + from + " - " + to);
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
