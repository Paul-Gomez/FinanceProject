package com.fincore.subscriptions.infrastructure.web;

import com.fincore.identity.infrastructure.security.CurrentUserId;
import com.fincore.subscriptions.application.SubscriptionService;
import com.fincore.subscriptions.application.SubscriptionService.SubscriptionData;
import com.fincore.subscriptions.domain.BillingCycle;
import com.fincore.subscriptions.domain.Subscription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final Clock clock;

    public SubscriptionController(SubscriptionService subscriptionService, Clock clock) {
        this.subscriptionService = subscriptionService;
        this.clock = clock;
    }

    public record SubscriptionRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @Positive BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            @NotNull BillingCycle billingCycle,
            @NotNull LocalDate startDate,
            UUID categoryId,
            UUID accountId) {

        SubscriptionData toData() {
            return new SubscriptionData(name, amount, currencyCode, billingCycle, startDate, categoryId, accountId);
        }
    }

    public record SubscriptionResponse(UUID id, String name, BigDecimal amount, String currencyCode,
                                       BillingCycle billingCycle, LocalDate startDate, LocalDate nextChargeDate,
                                       UUID categoryId, UUID accountId) {
    }

    public record UpcomingChargeResponse(UUID subscriptionId, String name, LocalDate date, BigDecimal amount,
                                         String currencyCode) {
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(@CurrentUserId UUID userId,
                                                       @Valid @RequestBody SubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(subscriptionService.create(userId, request.toData())));
    }

    @GetMapping
    public List<SubscriptionResponse> list(@CurrentUserId UUID userId) {
        return subscriptionService.listActive(userId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{subscriptionId}")
    public SubscriptionResponse get(@CurrentUserId UUID userId, @PathVariable UUID subscriptionId) {
        return toResponse(subscriptionService.get(subscriptionId, userId));
    }

    @PutMapping("/{subscriptionId}")
    public SubscriptionResponse update(@CurrentUserId UUID userId, @PathVariable UUID subscriptionId,
                                       @Valid @RequestBody SubscriptionRequest request) {
        return toResponse(subscriptionService.update(subscriptionId, userId, request.toData()));
    }

    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> delete(@CurrentUserId UUID userId, @PathVariable UUID subscriptionId) {
        subscriptionService.deactivate(subscriptionId, userId);
        return ResponseEntity.noContent().build();
    }

    /** Gasto mensual y anual equivalente de las suscripciones activas, por moneda. */
    @GetMapping("/summary")
    public List<SubscriptionService.CurrencyTotals> summary(@CurrentUserId UUID userId) {
        return subscriptionService.totals(userId);
    }

    @GetMapping("/upcoming")
    public List<UpcomingChargeResponse> upcoming(@CurrentUserId UUID userId,
                                                 @RequestParam(defaultValue = "30") int days) {
        return subscriptionService.upcoming(userId, days).stream()
                .map(c -> new UpcomingChargeResponse(c.subscriptionId(), c.name(), c.date(), c.amount().amount(),
                        c.amount().currency().getCurrencyCode()))
                .toList();
    }

    private SubscriptionResponse toResponse(Subscription s) {
        return new SubscriptionResponse(s.getId(), s.getName(), s.amount().amount(), s.getCurrencyCode(),
                s.getBillingCycle(), s.getStartDate(), s.nextChargeOnOrAfter(LocalDate.now(clock)),
                s.getCategoryId(), s.getAccountId());
    }
}
