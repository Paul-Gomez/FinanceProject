package com.fincore.transactions.application;

import com.fincore.accounts.application.AccountBalanceService;
import com.fincore.accounts.application.AccountOpenedEvent;
import com.fincore.transactions.domain.Transaction;
import com.fincore.transactions.infrastructure.persistence.TransactionJpaRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * Registra el saldo inicial de una cuenta nueva como un movimiento ADJUSTMENT.
 * Es @EventListener (síncrono) y no @TransactionalEventListener(AFTER_COMMIT) a propósito:
 * se ejecuta dentro de la transacción que crea la cuenta, así que si esto falla tampoco se
 * crea la cuenta, y nunca puede quedar una cuenta con saldo que no salga de sus movimientos.
 */
@Component
public class OpeningBalanceListener {

    static final String OPENING_BALANCE_REASON = "OPENING_BALANCE";

    private final AccountBalanceService accountBalanceService;
    private final TransactionJpaRepository transactionRepository;

    public OpeningBalanceListener(AccountBalanceService accountBalanceService,
                                  TransactionJpaRepository transactionRepository) {
        this.accountBalanceService = accountBalanceService;
        this.transactionRepository = transactionRepository;
    }

    @EventListener
    public void onAccountOpened(AccountOpenedEvent event) {
        accountBalanceService.applyDelta(event.accountId(), event.ownerId(), event.initialBalance());
        transactionRepository.save(Transaction.adjustment(event.accountId(), event.initialBalance(),
                "Saldo inicial", LocalDate.now(), Map.of("reason", OPENING_BALANCE_REASON)));
    }
}
