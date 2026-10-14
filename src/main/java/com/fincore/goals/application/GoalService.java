package com.fincore.goals.application;

import com.fincore.goals.domain.GoalContribution;
import com.fincore.goals.domain.GoalProgress;
import com.fincore.goals.domain.InvalidGoalOperationException;
import com.fincore.goals.domain.SavingsGoal;
import com.fincore.goals.infrastructure.persistence.GoalContributionRepository;
import com.fincore.goals.infrastructure.persistence.SavingsGoalRepository;
import com.fincore.shared.money.Money;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class GoalService {

    public enum ContributionType { DEPOSIT, WITHDRAWAL }

    public record GoalView(SavingsGoal goal, GoalProgress progress) {
    }

    private final SavingsGoalRepository goalRepository;
    private final GoalContributionRepository contributionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public GoalService(SavingsGoalRepository goalRepository, GoalContributionRepository contributionRepository,
                       ApplicationEventPublisher eventPublisher, Clock clock) {
        this.goalRepository = goalRepository;
        this.contributionRepository = contributionRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public GoalView create(UUID ownerId, String name, BigDecimal targetAmount, String currencyCode, LocalDate targetDate) {
        if (targetDate != null && targetDate.isBefore(today())) {
            throw new InvalidGoalOperationException("la fecha objetivo no puede estar en el pasado");
        }
        Money target = Money.ofExact(targetAmount, Money.currencyFor(currencyCode));
        return view(goalRepository.save(new SavingsGoal(ownerId, name.trim(), target, targetDate)));
    }

    @Transactional(readOnly = true)
    public List<GoalView> list(UUID ownerId) {
        return goalRepository.findAllByOwnerIdOrderByCreatedAtAsc(ownerId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public GoalView get(UUID goalId, UUID ownerId) {
        return view(owned(goalRepository.findById(goalId).orElse(null), goalId, ownerId));
    }

    @Transactional
    public GoalView update(UUID goalId, UUID ownerId, String name, BigDecimal targetAmount, LocalDate targetDate) {
        SavingsGoal goal = owned(goalRepository.findByIdForUpdate(goalId).orElse(null), goalId, ownerId);
        Money target = Money.ofExact(targetAmount, Money.currencyFor(goal.getCurrencyCode()));
        if (goal.update(name.trim(), target, targetDate, clock.instant())) {
            eventPublisher.publishEvent(new GoalAchievedEvent(ownerId, goal.getId(), goal.getName()));
        }
        return view(goal);
    }

    /** La aportación queda registrada y el saldo del objetivo se actualiza en la misma transacción. */
    @Transactional
    public GoalView contribute(UUID goalId, UUID ownerId, BigDecimal amount, ContributionType type,
                               LocalDate date, String note) {
        SavingsGoal goal = owned(goalRepository.findByIdForUpdate(goalId).orElse(null), goalId, ownerId);
        Money value = Money.ofExact(amount, Money.currencyFor(goal.getCurrencyCode()));
        if (!value.isPositive()) {
            throw new InvalidGoalOperationException("el importe tiene que ser mayor que cero");
        }
        Money signed = type == ContributionType.WITHDRAWAL ? value.negate() : value;
        Instant now = clock.instant();

        boolean achieved = goal.contribute(signed, now);
        contributionRepository.save(new GoalContribution(goal.getId(), signed.amount(),
                date != null ? date : today(), note, now));

        if (achieved) {
            eventPublisher.publishEvent(new GoalAchievedEvent(ownerId, goal.getId(), goal.getName()));
        }
        return view(goal);
    }

    @Transactional(readOnly = true)
    public List<GoalContribution> contributions(UUID goalId, UUID ownerId) {
        owned(goalRepository.findById(goalId).orElse(null), goalId, ownerId);
        return contributionRepository.findAllByGoalIdOrderByDateDescCreatedAtDesc(goalId);
    }

    @Transactional
    public GoalView cancel(UUID goalId, UUID ownerId) {
        SavingsGoal goal = owned(goalRepository.findByIdForUpdate(goalId).orElse(null), goalId, ownerId);
        goal.cancel(clock.instant());
        return view(goal);
    }

    private GoalView view(SavingsGoal goal) {
        return new GoalView(goal, GoalProgress.of(goal.target(), goal.saved(), goal.getTargetDate(),
                goal.getStatus(), today()));
    }

    private static SavingsGoal owned(SavingsGoal goal, UUID goalId, UUID ownerId) {
        if (goal == null || !goal.getOwnerId().equals(ownerId)) {
            throw new GoalNotFoundException(goalId);
        }
        return goal;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
