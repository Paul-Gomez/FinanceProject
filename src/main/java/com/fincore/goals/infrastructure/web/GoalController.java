package com.fincore.goals.infrastructure.web;

import com.fincore.goals.application.GoalService;
import com.fincore.goals.application.GoalService.ContributionType;
import com.fincore.goals.domain.GoalContribution;
import com.fincore.goals.domain.GoalStatus;
import com.fincore.identity.infrastructure.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    public record CreateGoalRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @Positive BigDecimal targetAmount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            LocalDate targetDate) {
    }

    public record UpdateGoalRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @Positive BigDecimal targetAmount,
            LocalDate targetDate) {
    }

    public record ContributionRequest(
            @NotNull @Positive BigDecimal amount,
            @NotNull ContributionType type,
            LocalDate date,
            @Size(max = 255) String note) {
    }

    public record GoalResponse(UUID id, String name, GoalStatus status, String currencyCode, BigDecimal targetAmount,
                               BigDecimal savedAmount, BigDecimal remainingAmount, BigDecimal progressPercentage,
                               LocalDate targetDate, Long monthsLeft, BigDecimal monthlyRecommended, boolean overdue,
                               Instant achievedAt) {

        static GoalResponse from(GoalService.GoalView view) {
            var goal = view.goal();
            var progress = view.progress();
            return new GoalResponse(goal.getId(), goal.getName(), goal.getStatus(), goal.getCurrencyCode(),
                    goal.target().amount(), progress.saved().amount(), progress.remaining().amount(),
                    progress.percentage(), goal.getTargetDate(), progress.monthsLeft(),
                    progress.monthlyRecommended() != null ? progress.monthlyRecommended().amount() : null,
                    progress.overdue(), goal.getAchievedAt());
        }
    }

    public record ContributionResponse(UUID id, BigDecimal amount, LocalDate date, String note, Instant createdAt) {
        static ContributionResponse from(GoalContribution c) {
            return new ContributionResponse(c.getId(), c.getAmount(), c.getDate(), c.getNote(), c.getCreatedAt());
        }
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(@CurrentUserId UUID userId, @Valid @RequestBody CreateGoalRequest request) {
        var view = goalService.create(userId, request.name(), request.targetAmount(), request.currencyCode(),
                request.targetDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(GoalResponse.from(view));
    }

    @GetMapping
    public List<GoalResponse> list(@CurrentUserId UUID userId) {
        return goalService.list(userId).stream().map(GoalResponse::from).toList();
    }

    @GetMapping("/{goalId}")
    public GoalResponse get(@CurrentUserId UUID userId, @PathVariable UUID goalId) {
        return GoalResponse.from(goalService.get(goalId, userId));
    }

    @PutMapping("/{goalId}")
    public GoalResponse update(@CurrentUserId UUID userId, @PathVariable UUID goalId,
                               @Valid @RequestBody UpdateGoalRequest request) {
        return GoalResponse.from(goalService.update(goalId, userId, request.name(), request.targetAmount(),
                request.targetDate()));
    }

    @PostMapping("/{goalId}/contributions")
    public GoalResponse contribute(@CurrentUserId UUID userId, @PathVariable UUID goalId,
                                   @Valid @RequestBody ContributionRequest request) {
        return GoalResponse.from(goalService.contribute(goalId, userId, request.amount(), request.type(),
                request.date(), request.note()));
    }

    @GetMapping("/{goalId}/contributions")
    public List<ContributionResponse> contributions(@CurrentUserId UUID userId, @PathVariable UUID goalId) {
        return goalService.contributions(goalId, userId).stream().map(ContributionResponse::from).toList();
    }

    @PostMapping("/{goalId}/cancel")
    public GoalResponse cancel(@CurrentUserId UUID userId, @PathVariable UUID goalId) {
        return GoalResponse.from(goalService.cancel(goalId, userId));
    }
}
