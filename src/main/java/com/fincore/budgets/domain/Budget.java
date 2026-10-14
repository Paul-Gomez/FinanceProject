package com.fincore.budgets.domain;

import com.fincore.shared.money.CurrencyMismatchException;
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
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, updatable = false)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private BudgetPeriod period;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Budget() {
    }

    public Budget(UUID ownerId, UUID categoryId, Money amount, BudgetPeriod period) {
        requirePositive(amount);
        this.ownerId = ownerId;
        this.categoryId = categoryId;
        this.amount = amount.amount();
        this.currencyCode = amount.currency().getCurrencyCode();
        this.period = period;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void changeAmount(Money newAmount) {
        requirePositive(newAmount);
        if (!newAmount.currency().getCurrencyCode().equals(currencyCode)) {
            throw new CurrencyMismatchException(amount().currency(), newAmount.currency());
        }
        this.amount = newAmount.amount();
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    private static void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("El importe de un presupuesto debe ser mayor que cero");
        }
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

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BudgetPeriod getPeriod() {
        return period;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
