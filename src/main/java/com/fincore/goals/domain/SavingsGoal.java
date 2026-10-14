package com.fincore.goals.domain;

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
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "savings_goals")
public class SavingsGoal {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(name = "target_amount", nullable = false)
    private BigDecimal targetAmount;

    @Column(name = "saved_amount", nullable = false)
    private BigDecimal savedAmount;

    @Column(name = "currency_code", nullable = false, updatable = false)
    private String currencyCode;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalStatus status;

    @Column(name = "achieved_at")
    private Instant achievedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavingsGoal() {
    }

    public SavingsGoal(UUID ownerId, String name, Money target, LocalDate targetDate) {
        requirePositive(target);
        this.ownerId = ownerId;
        this.name = name;
        this.targetAmount = target.amount();
        this.savedAmount = Money.zero(target.currency()).amount();
        this.currencyCode = target.currency().getCurrencyCode();
        this.targetDate = targetDate;
        this.status = GoalStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Aporta (importe positivo) o retira (negativo). Devuelve true si con este movimiento el objetivo
     * pasa a estar conseguido, para que quien llama pueda avisar.
     */
    public boolean contribute(Money signedAmount, Instant now) {
        if (status == GoalStatus.CANCELLED) {
            throw new InvalidGoalOperationException("el objetivo está cancelado");
        }
        Money newSaved = saved().add(signedAmount);
        if (newSaved.isNegative()) {
            throw new InvalidGoalOperationException("no puedes retirar más de lo ahorrado (" + saved() + ")");
        }
        this.savedAmount = newSaved.amount();
        this.updatedAt = now;
        return refreshStatus(now);
    }

    public boolean update(String newName, Money newTarget, LocalDate newTargetDate, Instant now) {
        requirePositive(newTarget);
        if (status == GoalStatus.CANCELLED) {
            throw new InvalidGoalOperationException("el objetivo está cancelado");
        }
        if (!newTarget.currency().getCurrencyCode().equals(currencyCode)) {
            throw new CurrencyMismatchException(target().currency(), newTarget.currency());
        }
        this.name = newName;
        this.targetAmount = newTarget.amount();
        this.targetDate = newTargetDate;
        this.updatedAt = now;
        return refreshStatus(now);
    }

    public void cancel(Instant now) {
        if (status != GoalStatus.ACTIVE) {
            throw new InvalidGoalOperationException("solo se puede cancelar un objetivo activo (está " + status + ")");
        }
        this.status = GoalStatus.CANCELLED;
        this.updatedAt = now;
    }

    /** ACTIVE <-> ACHIEVED según lo ahorrado. Devuelve true solo en la transición a ACHIEVED. */
    private boolean refreshStatus(Instant now) {
        boolean reached = saved().compareTo(target()) >= 0;
        if (status == GoalStatus.ACTIVE && reached) {
            status = GoalStatus.ACHIEVED;
            achievedAt = now;
            return true;
        }
        if (status == GoalStatus.ACHIEVED && !reached) {
            status = GoalStatus.ACTIVE;
            achievedAt = null;
        }
        return false;
    }

    private static void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new InvalidGoalOperationException("el importe objetivo tiene que ser mayor que cero");
        }
    }

    public Money target() {
        return Money.of(targetAmount, Currency.getInstance(currencyCode));
    }

    public Money saved() {
        return Money.of(savedAmount, Currency.getInstance(currencyCode));
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

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public GoalStatus getStatus() {
        return status;
    }

    public Instant getAchievedAt() {
        return achievedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
