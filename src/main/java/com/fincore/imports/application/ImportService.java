package com.fincore.imports.application;

import com.fincore.accounts.application.AccountService;
import com.fincore.accounts.domain.Account;
import com.fincore.accounts.domain.AccountNotActiveException;
import com.fincore.accounts.domain.AccountStatus;
import com.fincore.categories.application.CategoryRuleMatcher;
import com.fincore.categories.application.CategoryRuleService;
import com.fincore.categories.domain.CategoryKind;
import com.fincore.imports.application.parsing.AmountParser;
import com.fincore.imports.application.parsing.ColumnMapping;
import com.fincore.imports.application.parsing.CsvFileReader;
import com.fincore.imports.application.parsing.CsvFileReader.CsvContent;
import com.fincore.imports.application.parsing.CsvFileReader.CsvRow;
import com.fincore.imports.application.parsing.DateParser;
import com.fincore.imports.application.parsing.Fingerprints;
import com.fincore.imports.domain.ImportJob;
import com.fincore.imports.domain.ImportRowResult;
import com.fincore.imports.domain.ImportRowResult.Status;
import com.fincore.imports.infrastructure.persistence.ImportJobRepository;
import com.fincore.imports.infrastructure.persistence.ImportRowRepository;
import com.fincore.shared.audit.AuditAction;
import com.fincore.shared.audit.AuditEvent;
import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;
import com.fincore.shared.money.Money;
import com.fincore.transactions.application.ImportedTransaction;
import com.fincore.transactions.application.ImportedTransactionWriter;
import com.fincore.transactions.domain.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Importación de movimientos desde CSV.
 * 1. Se lee el fichero y se detectan las columnas (si no se puede, 422 y no se crea nada).
 * 2. Se valida cada fila y se clasifica: error (con el motivo), duplicada o candidata.
 * 3. Las candidatas se insertan en lotes de BATCH_SIZE, cada uno en su propia transacción, con una
 *    sola actualización de saldo por lote. Así una importación grande no tiene una transacción
 *    gigante abierta, y si se corta a mitad lo confirmado es consistente: reimportar el mismo
 *    fichero es seguro porque lo ya importado sale como duplicado gracias a las huellas.
 * Se hace en la propia petición: con el límite de filas cabe en unos segundos. Para ficheros mucho
 * más grandes se pasaría a un proceso en segundo plano y el cliente consultaría el estado del job.
 */
@Service
public class ImportService {

    static final int MAX_ROWS = 20_000;
    static final int BATCH_SIZE = 500;
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    public static class ImportNotFoundException extends BusinessException {
        public ImportNotFoundException(UUID id) {
            super(ErrorType.NOT_FOUND, "IMPORT_NOT_FOUND", "No existe ninguna importación con id " + id);
        }
    }

    /** Fila válida pendiente de insertar. */
    private record Candidate(CsvRow row, ImportedTransaction transaction) {
    }

    private final ImportJobRepository jobRepository;
    private final ImportRowRepository rowRepository;
    private final AccountService accountService;
    private final ImportedTransactionWriter transactionWriter;
    private final CategoryRuleService categoryRuleService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ImportService(ImportJobRepository jobRepository, ImportRowRepository rowRepository,
                         AccountService accountService, ImportedTransactionWriter transactionWriter,
                         CategoryRuleService categoryRuleService, ApplicationEventPublisher eventPublisher,
                         PlatformTransactionManager transactionManager, Clock clock) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.accountService = accountService;
        this.transactionWriter = transactionWriter;
        this.categoryRuleService = categoryRuleService;
        this.eventPublisher = eventPublisher;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public ImportJob importCsv(UUID ownerId, UUID accountId, String fileName, byte[] content) {
        Account account = accountService.getForOwner(accountId, ownerId);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(accountId, account.getStatus());
        }
        Currency currency = account.balance().currency();

        CsvContent csv = CsvFileReader.read(content, MAX_ROWS);
        ColumnMapping columns = ColumnMapping.detect(csv.headers());

        ImportJob job = transactions.execute(status -> jobRepository.save(
                new ImportJob(ownerId, accountId, fileName, clock.instant())));
        try {
            return process(job, ownerId, accountId, currency, csv, columns);
        } catch (RuntimeException e) {
            log.error("La importación {} ha fallado", job.getId(), e);
            return transactions.execute(status -> {
                ImportJob failed = jobRepository.findById(job.getId()).orElseThrow();
                failed.fail("Error inesperado procesando el fichero; lo ya importado se ha guardado y se puede "
                        + "volver a subir el mismo fichero sin duplicar nada", clock.instant());
                return failed;
            });
        }
    }

    private ImportJob process(ImportJob job, UUID ownerId, UUID accountId, Currency currency, CsvContent csv,
                              ColumnMapping columns) {
        CategoryRuleMatcher matcher = categoryRuleService.matcherFor(ownerId);
        LocalDate today = LocalDate.now(clock);

        List<ImportRowResult> results = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        Map<String, Integer> occurrences = new HashMap<>();
        Map<String, Long> firstRowByFingerprint = new HashMap<>();
        Character decimalSeparator = AmountParser.detectDecimalSeparator(amountValues(csv, columns));

        for (CsvRow row : csv.rows()) {
            try {
                ImportedTransaction tx = parseRow(row, columns, accountId, currency, today, matcher, occurrences,
                        decimalSeparator);
                Long firstRow = firstRowByFingerprint.putIfAbsent(tx.fingerprint(), row.rowNumber());
                if (firstRow != null) {
                    results.add(result(row, tx, Status.DUPLICATE, "Repetida dentro del fichero (fila " + firstRow + ")", null));
                } else {
                    candidates.add(new Candidate(row, tx));
                }
            } catch (IllegalArgumentException | BusinessException e) {
                results.add(new ImportRowResult(row.rowNumber(), Status.ERROR, null, null, null, e.getMessage(), null));
            }
        }

        Set<String> alreadyImported = transactionWriter.existingFingerprints(accountId,
                candidates.stream().map(c -> c.transaction().fingerprint()).toList());
        List<Candidate> toInsert = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (alreadyImported.contains(candidate.transaction().fingerprint())) {
                results.add(result(candidate.row(), candidate.transaction(), Status.DUPLICATE, "Ya estaba importada", null));
            } else {
                toInsert.add(candidate);
            }
        }

        for (int from = 0; from < toInsert.size(); from += BATCH_SIZE) {
            List<Candidate> batch = toInsert.subList(from, Math.min(from + BATCH_SIZE, toInsert.size()));
            results.addAll(transactions.execute(status -> insertBatch(job, ownerId, accountId, currency, batch)));
        }

        return transactions.execute(status -> finish(job.getId(), csv.rows().size(), results));
    }

    private List<ImportRowResult> insertBatch(ImportJob job, UUID ownerId, UUID accountId, Currency currency,
                                              List<Candidate> batch) {
        Map<String, UUID> inserted = transactionWriter.writeBatch(ownerId, accountId, currency, job.getId(),
                batch.stream().map(Candidate::transaction).toList());

        List<ImportRowResult> batchResults = new ArrayList<>();
        for (Candidate c : batch) {
            UUID id = inserted.get(c.transaction().fingerprint());
            // Si no se insertó es porque otra importación metió el mismo movimiento justo ahora.
            batchResults.add(id != null
                    ? result(c.row(), c.transaction(), Status.IMPORTED, null, id)
                    : result(c.row(), c.transaction(), Status.DUPLICATE, "Ya estaba importada", null));
        }
        rowRepository.saveAll(job.getId(), batchResults);
        return batchResults;
    }

    private ImportJob finish(UUID jobId, int totalRows, List<ImportRowResult> results) {
        rowRepository.saveAll(jobId, results.stream().filter(r -> r.status() != Status.IMPORTED).toList());

        int imported = count(results, Status.IMPORTED);
        int duplicates = count(results, Status.DUPLICATE);
        int errors = count(results, Status.ERROR);
        ImportJob job = jobRepository.findById(jobId).orElseThrow();
        job.complete(totalRows, imported, duplicates, errors, clock.instant());

        eventPublisher.publishEvent(new ImportCompletedEvent(job.getOwnerId(), job.getId(), job.getAccountId(),
                imported, duplicates, errors));
        eventPublisher.publishEvent(AuditEvent.of(job.getOwnerId(), AuditAction.IMPORT_TRANSACTIONS, "IMPORT", job.getId(),
                Map.of("imported", imported, "duplicates", duplicates, "errors", errors)));
        return job;
    }

    private static ImportedTransaction parseRow(CsvRow row, ColumnMapping columns, UUID accountId, Currency currency,
                                                LocalDate today, CategoryRuleMatcher matcher,
                                                Map<String, Integer> occurrences, Character decimalSeparator) {
        LocalDate date = DateParser.parse(value(row, columns.date()));
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("la fecha " + date + " es futura");
        }

        BigDecimal amount = readAmount(row, columns, decimalSeparator);
        if (amount.signum() == 0) {
            throw new IllegalArgumentException("el importe es cero");
        }
        // Rechaza importes con más decimales de los que admite la moneda de la cuenta.
        amount = Money.ofExact(amount, currency).amount();

        String description = truncate(value(row, columns.description()), 255);
        String reference = truncate(value(row, columns.reference()), 100);
        TransactionType type = amount.signum() > 0 ? TransactionType.INCOME : TransactionType.EXPENSE;
        CategoryKind kind = type == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
        UUID categoryId = matcher.match(description, kind).map(CategoryRuleMatcher.CompiledRule::categoryId).orElse(null);

        String fingerprint;
        if (reference != null && !reference.isBlank()) {
            fingerprint = Fingerprints.withReference(accountId, reference);
        } else {
            int occurrence = occurrences.merge(Fingerprints.occurrenceKey(date, amount, description), 1, Integer::sum);
            fingerprint = Fingerprints.withoutReference(accountId, date, amount, description, occurrence);
        }
        return new ImportedTransaction(fingerprint, type, amount, date, description,
                reference == null || reference.isBlank() ? null : reference, categoryId);
    }

    /** Una columna de importe con signo, o columnas separadas de cargo y abono. */
    private static BigDecimal readAmount(CsvRow row, ColumnMapping columns, Character decimalSeparator) {
        if (columns.amount() >= 0) {
            return AmountParser.parse(value(row, columns.amount()), decimalSeparator);
        }
        String debit = value(row, columns.debit());
        String credit = value(row, columns.credit());
        BigDecimal amount = BigDecimal.ZERO;
        if (debit != null && !debit.isBlank()) {
            amount = amount.subtract(AmountParser.parse(debit, decimalSeparator).abs());
        }
        if (credit != null && !credit.isBlank()) {
            amount = amount.add(AmountParser.parse(credit, decimalSeparator).abs());
        }
        return amount;
    }

    /** Todos los valores de las columnas de importe, para deducir la convención decimal del fichero. */
    private static List<String> amountValues(CsvContent csv, ColumnMapping columns) {
        List<String> values = new ArrayList<>();
        for (CsvRow row : csv.rows()) {
            values.add(value(row, columns.amount()));
            values.add(value(row, columns.debit()));
            values.add(value(row, columns.credit()));
        }
        return values;
    }

    private static String value(CsvRow row, int column) {
        return column >= 0 && column < row.values().size() ? row.values().get(column) : null;
    }

    private static ImportRowResult result(CsvRow row, ImportedTransaction tx, Status status, String message, UUID txId) {
        return new ImportRowResult(row.rowNumber(), status, tx.date(), tx.signedAmount(), tx.description(), message, txId);
    }

    private static int count(List<ImportRowResult> results, Status status) {
        return (int) results.stream().filter(r -> r.status() == status).count();
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }

    public List<ImportJob> list(UUID ownerId) {
        return jobRepository.findTop50ByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    public ImportJob get(UUID jobId, UUID ownerId) {
        return jobRepository.findById(jobId)
                .filter(job -> job.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new ImportNotFoundException(jobId));
    }

    public List<ImportRowResult> rows(UUID jobId, UUID ownerId, Status status) {
        get(jobId, ownerId);
        return rowRepository.findByJob(jobId, status).stream()
                .sorted(Comparator.comparingLong(ImportRowResult::rowNumber))
                .toList();
    }
}
