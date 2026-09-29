package com.fincore.accounts.domain;

import com.fincore.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(name = "currency_code", nullable = false, updatable = false)
    private String currencyCode;

    @Column(nullable = false)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
    }

    public Account(UUID ownerId, String name, AccountType type, Money initialBalance) {
        this.ownerId = ownerId;
        this.name = name;
        this.type = type;
        this.currencyCode = initialBalance.currency().getCurrencyCode();
        this.balance = initialBalance.amount();
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void rename(String newName) {
        this.name = newName;
        this.updatedAt = Instant.now();
    }

    public void changeStatus(AccountStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new InvalidAccountStatusTransitionException(this.status, target);
        }
        this.status = target;
        this.updatedAt = Instant.now();
    }

    public Money balance() {
        return Money.of(balance, Currency.getInstance(currencyCode));
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

    public AccountType getType() {
        return type;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
