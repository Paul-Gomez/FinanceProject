package com.fincore.accounts.application;

import com.fincore.shared.money.Money;

import java.util.UUID;

/**
 * Se publica al crear una cuenta con saldo inicial distinto de cero. Quien lo escuche tiene que
 * registrar ese saldo dentro de la misma transacción (con @EventListener, no AFTER_COMMIT).
 */
public record AccountOpenedEvent(UUID accountId, UUID ownerId, Money initialBalance) {
}
