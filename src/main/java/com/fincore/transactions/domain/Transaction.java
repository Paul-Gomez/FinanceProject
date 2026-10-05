package com.fincore.transactions.domain;

import com.fincore.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, updatable = false)
    private String currencyCode;

    @Column(name = "category_id")
    private UUID categoryId;

    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    private String reference;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    private Transaction(UUID accountId, TransactionType type, Money signedAmount, UUID categoryId,
                        String description, LocalDate transactionDate, String reference,
                        Map<String, Object> metadata) {
        this.accountId = accountId;
        this.type = type;
        this.status = TransactionStatus.POSTED;
        this.amount = signedAmount.amount();
        this.currencyCode = signedAmount.currency().getCurrencyCode();
        this.categoryId = categoryId;
        this.description = description;
        this.transactionDate = transactionDate;
        this.reference = reference;
        this.metadata = metadata;
        this.createdAt = Instant.now();
    }

    /** El importe que llega es siempre positivo; aquí se convierte al importe con signo. */
    public static Transaction income(UUID accountId, Money amount, UUID categoryId, String description,
                                     LocalDate date, String reference, Map<String, Object> metadata) {
        requirePositive(amount);
        return new Transaction(accountId, TransactionType.INCOME, amount, categoryId, description, date, reference, metadata);
    }

    public static Transaction expense(UUID accountId, Money amount, UUID categoryId, String description,
                                      LocalDate date, String reference, Map<String, Object> metadata) {
        requirePositive(amount);
        return new Transaction(accountId, TransactionType.EXPENSE, amount.negate(), categoryId, description, date, reference, metadata);
    }

    private static void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("El importe del movimiento debe ser mayor que cero");
        }
    }

    public Money signedAmount() {
        return Money.of(amount, Currency.getInstance(currencyCode));
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public String getReference() {
        return reference;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
