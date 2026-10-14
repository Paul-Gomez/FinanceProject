package com.fincore.subscriptions.domain;

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
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    private BillingCycle billingCycle;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {
    }

    public Subscription(UUID ownerId, String name, Money amount, BillingCycle billingCycle, LocalDate startDate,
                        UUID categoryId, UUID accountId) {
        this.ownerId = ownerId;
        this.active = true;
        this.createdAt = Instant.now();
        apply(name, amount, billingCycle, startDate, categoryId, accountId);
    }

    public void update(String name, Money amount, BillingCycle billingCycle, LocalDate startDate,
                       UUID categoryId, UUID accountId) {
        apply(name, amount, billingCycle, startDate, categoryId, accountId);
    }

    private void apply(String name, Money amount, BillingCycle billingCycle, LocalDate startDate,
                       UUID categoryId, UUID accountId) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("El importe de una suscripción debe ser mayor que cero");
        }
        this.name = name;
        this.amount = amount.amount();
        this.currencyCode = amount.currency().getCurrencyCode();
        this.billingCycle = billingCycle;
        this.startDate = startDate;
        this.categoryId = categoryId;
        this.accountId = accountId;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public LocalDate nextChargeOnOrAfter(LocalDate date) {
        return billingCycle.nextChargeOnOrAfter(startDate, date);
    }

    /** Todos los cobros entre las dos fechas (ambas incluidas). */
    public List<LocalDate> chargesBetween(LocalDate from, LocalDate to) {
        List<LocalDate> charges = new ArrayList<>();
        for (LocalDate next = nextChargeOnOrAfter(from); !next.isAfter(to);
             next = billingCycle.nextChargeOnOrAfter(startDate, next.plusDays(1))) {
            charges.add(next);
        }
        return charges;
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

    public String getName() {
        return name;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public boolean isActive() {
        return active;
    }
}
