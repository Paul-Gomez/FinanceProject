package com.fincore.transactions.application;

import com.fincore.accounts.application.AccountBalanceService;
import com.fincore.shared.money.Money;
import com.fincore.transactions.infrastructure.persistence.ImportedTransactionJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** API pública para que el módulo de importación escriba movimientos en bloque. */
@Service
public class ImportedTransactionWriter {

    private final ImportedTransactionJdbcRepository repository;
    private final AccountBalanceService accountBalanceService;

    public ImportedTransactionWriter(ImportedTransactionJdbcRepository repository,
                                     AccountBalanceService accountBalanceService) {
        this.repository = repository;
        this.accountBalanceService = accountBalanceService;
    }

    /**
     * Inserta un lote y actualiza el saldo UNA vez con la suma de lo que se ha insertado de verdad
     * (los duplicados descartados por la base de datos no cuentan). Exige transacción abierta:
     * movimientos y saldo del lote se confirman juntos.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, UUID> writeBatch(UUID ownerId, UUID accountId, Currency currency, UUID importJobId,
                                        List<ImportedTransaction> batch) {
        Map<String, UUID> inserted = repository.insertIgnoringDuplicates(accountId, currency.getCurrencyCode(),
                importJobId, batch);

        BigDecimal delta = batch.stream()
                .filter(tx -> inserted.containsKey(tx.fingerprint()))
                .map(ImportedTransaction::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (delta.signum() != 0) {
            accountBalanceService.applyDelta(accountId, ownerId, Money.of(delta, currency));
        }
        return inserted;
    }

    @Transactional(readOnly = true)
    public Set<String> existingFingerprints(UUID accountId, Collection<String> fingerprints) {
        return repository.existingFingerprints(accountId, fingerprints);
    }
}
