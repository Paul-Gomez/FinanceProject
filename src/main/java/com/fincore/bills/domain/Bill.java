package com.fincore.bills.domain;

import com.fincore.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "bills")
public class Bill {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_transaction_id")
    private UUID paidTransactionId;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Bill() {
    }

    /** Si al registrarla ya ha vencido, nace como OVERDUE. */
    public Bill(UUID ownerId, String provider, Money amount, LocalDate issueDate, LocalDate dueDate,
                UUID categoryId, String notes, LocalDate today, Instant now) {
        this.ownerId = ownerId;
        this.createdAt = now;
        apply(provider, amount, issueDate, dueDate, categoryId, notes, now);
        this.status = dueDate.isBefore(today) ? BillStatus.OVERDUE : BillStatus.PENDING;
    }

    /** Solo se pueden editar las facturas abiertas (pendientes o vencidas). */
    public void edit(String provider, Money amount, LocalDate issueDate, LocalDate dueDate, UUID categoryId,
                     String notes, LocalDate today, Instant now) {
        if (!status.isOpen()) {
            throw new InvalidBillTransitionException(status, "modificar");
        }
        apply(provider, amount, issueDate, dueDate, categoryId, notes, now);
        // Si se cambia el vencimiento, el estado se recalcula: una vencida con nueva fecha futura vuelve a pendiente.
        this.status = dueDate.isBefore(today) ? BillStatus.OVERDUE : BillStatus.PENDING;
    }

    public void markPaid(UUID transactionId, Instant now) {
        transitionTo(BillStatus.PAID, now);
        this.paidAt = now;
        this.paidTransactionId = transactionId;
    }

    public void cancel(Instant now) {
        transitionTo(BillStatus.CANCELLED, now);
    }

    private void transitionTo(BillStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidBillTransitionException(status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    private void apply(String provider, Money amount, LocalDate issueDate, LocalDate dueDate, UUID categoryId,
                       String notes, Instant now) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("El importe de una factura debe ser mayor que cero");
        }
        if (dueDate.isBefore(issueDate)) {
            throw new InvalidBillDatesException();
        }
        this.provider = provider;
        this.amount = amount.amount();
        this.currencyCode = amount.currency().getCurrencyCode();
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.categoryId = categoryId;
        this.notes = notes;
        this.updatedAt = now;
    }

    public Money amount() {
        return Money.of(amount, Currency.getInstance(currencyCode));
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getProvider() {
        return provider;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public BillStatus getStatus() {
        return status;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public UUID getPaidTransactionId() {
        return paidTransactionId;
    }

    public String getNotes() {
        return notes;
    }
}
