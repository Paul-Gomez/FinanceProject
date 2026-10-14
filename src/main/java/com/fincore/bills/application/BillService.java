package com.fincore.bills.application;

import com.fincore.bills.domain.Bill;
import com.fincore.bills.domain.BillStatus;
import com.fincore.bills.domain.InvalidBillTransitionException;
import com.fincore.bills.infrastructure.persistence.BillRepository;
import com.fincore.bills.infrastructure.persistence.OverdueBillsRepository;
import com.fincore.bills.infrastructure.persistence.OverdueBillsRepository.OverdueBill;
import com.fincore.categories.application.CategoryQueryService;
import com.fincore.categories.domain.CategoryKind;
import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;
import com.fincore.shared.money.Money;
import com.fincore.transactions.application.RecordTransactionCommand;
import com.fincore.transactions.application.TransactionService;
import com.fincore.transactions.domain.Transaction;
import com.fincore.transactions.domain.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BillService {

    public record BillData(String provider, BigDecimal amount, String currencyCode, LocalDate issueDate,
                           LocalDate dueDate, UUID categoryId, String notes) {
    }

    public static class BillNotFoundException extends BusinessException {
        public BillNotFoundException(UUID id) {
            super(ErrorType.NOT_FOUND, "BILL_NOT_FOUND", "No existe ninguna factura con id " + id);
        }
    }

    private final BillRepository billRepository;
    private final OverdueBillsRepository overdueBillsRepository;
    private final CategoryQueryService categoryQueryService;
    private final TransactionService transactionService;
    private final Clock clock;

    public BillService(BillRepository billRepository, OverdueBillsRepository overdueBillsRepository,
                       CategoryQueryService categoryQueryService, TransactionService transactionService, Clock clock) {
        this.billRepository = billRepository;
        this.overdueBillsRepository = overdueBillsRepository;
        this.categoryQueryService = categoryQueryService;
        this.transactionService = transactionService;
        this.clock = clock;
    }

    @Transactional
    public Bill create(UUID ownerId, BillData data) {
        Money amount = validate(ownerId, data);
        return billRepository.save(new Bill(ownerId, data.provider().trim(), amount, data.issueDate(), data.dueDate(),
                data.categoryId(), data.notes(), today(), clock.instant()));
    }

    @Transactional
    public Bill edit(UUID billId, UUID ownerId, BillData data) {
        Bill bill = owned(billRepository.findByIdForUpdate(billId).orElse(null), billId, ownerId);
        Money amount = validate(ownerId, data);
        bill.edit(data.provider().trim(), amount, data.issueDate(), data.dueDate(), data.categoryId(), data.notes(),
                today(), clock.instant());
        return bill;
    }

    @Transactional(readOnly = true)
    public List<Bill> list(UUID ownerId, BillStatus status) {
        return status == null
                ? billRepository.findAllByOwnerIdOrderByDueDateAsc(ownerId)
                : billRepository.findAllByOwnerIdAndStatusOrderByDueDateAsc(ownerId, status);
    }

    @Transactional(readOnly = true)
    public Bill get(UUID billId, UUID ownerId) {
        return owned(billRepository.findById(billId).orElse(null), billId, ownerId);
    }

    /**
     * Marca la factura como pagada y registra el gasto en la cuenta, todo en la misma transacción.
     * La factura se bloquea antes de nada: si llegan dos pagos a la vez, el segundo espera, ve que ya
     * está PAID y recibe un 409 en vez de crear otro gasto.
     */
    @Transactional
    public Bill pay(UUID billId, UUID ownerId, UUID accountId, LocalDate paymentDate) {
        Bill bill = owned(billRepository.findByIdForUpdate(billId).orElse(null), billId, ownerId);
        if (!bill.getStatus().canTransitionTo(BillStatus.PAID)) {
            throw new InvalidBillTransitionException(bill.getStatus(), BillStatus.PAID);
        }

        Transaction expense = transactionService.create(ownerId, new RecordTransactionCommand(
                accountId, TransactionType.EXPENSE, bill.amount().amount(), bill.getCurrencyCode(), bill.getCategoryId(),
                "Factura " + bill.getProvider(), paymentDate != null ? paymentDate : today(), "bill:" + bill.getId(),
                Map.of("billId", bill.getId().toString())));

        bill.markPaid(expense.getId(), clock.instant());
        return bill;
    }

    @Transactional
    public Bill cancel(UUID billId, UUID ownerId) {
        Bill bill = owned(billRepository.findByIdForUpdate(billId).orElse(null), billId, ownerId);
        bill.cancel(clock.instant());
        return bill;
    }

    /** Para el job diario. */
    @Transactional
    public List<OverdueBill> markOverdueBills() {
        return overdueBillsRepository.markOverdue(today(), clock.instant());
    }

    /** Pendientes que vencen entre hoy y dentro de N días (para los recordatorios). */
    @Transactional(readOnly = true)
    public List<Bill> pendingDueWithin(int days) {
        return billRepository.findPendingDueBetween(today(), today().plusDays(days));
    }

    /** Abiertas del usuario que vencen hasta la fecha indicada (vencidas incluidas), para el dashboard. */
    @Transactional(readOnly = true)
    public List<Bill> openDueUntil(UUID ownerId, LocalDate to) {
        return billRepository.findOpenDueUntil(ownerId, to);
    }

    private Money validate(UUID ownerId, BillData data) {
        Money amount = Money.ofExact(data.amount(), Money.currencyFor(data.currencyCode()));
        if (data.categoryId() != null) {
            categoryQueryService.requireUsable(data.categoryId(), ownerId, CategoryKind.EXPENSE);
        }
        return amount;
    }

    private static Bill owned(Bill bill, UUID billId, UUID ownerId) {
        if (bill == null || !bill.getOwnerId().equals(ownerId)) {
            throw new BillNotFoundException(billId);
        }
        return bill;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
