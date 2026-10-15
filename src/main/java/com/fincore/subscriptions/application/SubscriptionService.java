package com.fincore.subscriptions.application;

import com.fincore.accounts.application.AccountService;
import com.fincore.categories.application.CategoryQueryService;
import com.fincore.categories.domain.CategoryKind;
import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;
import com.fincore.shared.money.CurrencyMismatchException;
import com.fincore.shared.money.Money;
import com.fincore.subscriptions.domain.BillingCycle;
import com.fincore.subscriptions.domain.Subscription;
import com.fincore.subscriptions.infrastructure.persistence.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class SubscriptionService {

    static final int MAX_UPCOMING_DAYS = 366;

    public record SubscriptionData(String name, BigDecimal amount, String currencyCode, BillingCycle billingCycle,
                                   LocalDate startDate, UUID categoryId, UUID accountId) {
    }

    public record UpcomingCharge(UUID subscriptionId, UUID ownerId, String name, LocalDate date, Money amount) {
    }

    public record CurrencyTotals(String currencyCode, int subscriptions, BigDecimal monthly, BigDecimal yearly) {
    }

    public static class SubscriptionNotFoundException extends BusinessException {
        public SubscriptionNotFoundException(UUID id) {
            super(ErrorType.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND", "No existe ninguna suscripción con id " + id);
        }
    }

    private final SubscriptionRepository repository;
    private final CategoryQueryService categoryQueryService;
    private final AccountService accountService;
    private final Clock clock;

    public SubscriptionService(SubscriptionRepository repository, CategoryQueryService categoryQueryService,
                               AccountService accountService, Clock clock) {
        this.repository = repository;
        this.categoryQueryService = categoryQueryService;
        this.accountService = accountService;
        this.clock = clock;
    }

    @Transactional
    public Subscription create(UUID ownerId, SubscriptionData data) {
        Money amount = validate(ownerId, data);
        return repository.save(new Subscription(ownerId, data.name().trim(), amount, data.billingCycle(),
                data.startDate(), data.categoryId(), data.accountId()));
    }

    @Transactional
    public Subscription update(UUID subscriptionId, UUID ownerId, SubscriptionData data) {
        Subscription subscription = getOwned(subscriptionId, ownerId);
        Money amount = validate(ownerId, data);
        subscription.update(data.name().trim(), amount, data.billingCycle(), data.startDate(),
                data.categoryId(), data.accountId());
        return subscription;
    }

    @Transactional
    public void deactivate(UUID subscriptionId, UUID ownerId) {
        getOwned(subscriptionId, ownerId).deactivate();
    }

    @Transactional(readOnly = true)
    public List<Subscription> listActive(UUID ownerId) {
        return repository.findAllByOwnerIdAndActiveTrueOrderByNameAsc(ownerId);
    }

    @Transactional(readOnly = true)
    public Subscription get(UUID subscriptionId, UUID ownerId) {
        return getOwned(subscriptionId, ownerId);
    }

    /**
     * Gasto mensual y anual equivalente, por moneda. Se suma primero el equivalente anual exacto y se
     * divide entre 12 al final, redondeando una sola vez (redondear cada suscripción acumularía error).
     */
    @Transactional(readOnly = true)
    public List<CurrencyTotals> totals(UUID ownerId) {
        Map<String, BigDecimal> yearly = new TreeMap<>();
        Map<String, Integer> counts = new TreeMap<>();
        for (Subscription s : listActive(ownerId)) {
            yearly.merge(s.getCurrencyCode(), s.getBillingCycle().yearlyAmount(s.amount().amount()), BigDecimal::add);
            counts.merge(s.getCurrencyCode(), 1, Integer::sum);
        }
        return yearly.entrySet().stream()
                .map(e -> new CurrencyTotals(e.getKey(), counts.get(e.getKey()),
                        e.getValue().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP),
                        e.getValue().setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UpcomingCharge> upcoming(UUID ownerId, int days) {
        LocalDate from = LocalDate.now(clock);
        return chargesOf(listActive(ownerId), from, from.plusDays(Math.min(Math.max(days, 1), MAX_UPCOMING_DAYS)));
    }

    /** Cobros del usuario entre dos fechas (para la previsión de gastos recurrentes). */
    @Transactional(readOnly = true)
    public List<UpcomingCharge> chargesBetween(UUID ownerId, LocalDate from, LocalDate to) {
        return chargesOf(listActive(ownerId), from, to);
    }

    /** Próximos cobros de todos los usuarios (para el job de recordatorios). */
    @Transactional(readOnly = true)
    public List<UpcomingCharge> upcomingForEveryone(LocalDate from, LocalDate to) {
        return chargesOf(repository.findAllByActiveTrue(), from, to);
    }

    private static List<UpcomingCharge> chargesOf(List<Subscription> subscriptions, LocalDate from, LocalDate to) {
        List<UpcomingCharge> charges = new ArrayList<>();
        for (Subscription s : subscriptions) {
            for (LocalDate date : s.chargesBetween(from, to)) {
                charges.add(new UpcomingCharge(s.getId(), s.getOwnerId(), s.getName(), date, s.amount()));
            }
        }
        charges.sort(Comparator.comparing(UpcomingCharge::date).thenComparing(UpcomingCharge::name));
        return charges;
    }

    private Money validate(UUID ownerId, SubscriptionData data) {
        Money amount = Money.ofExact(data.amount(), Money.currencyFor(data.currencyCode()));
        if (data.categoryId() != null) {
            categoryQueryService.requireUsable(data.categoryId(), ownerId, CategoryKind.EXPENSE);
        }
        if (data.accountId() != null) {
            var account = accountService.getForOwner(data.accountId(), ownerId);
            if (!account.balance().currency().equals(amount.currency())) {
                throw new CurrencyMismatchException(account.balance().currency(), amount.currency());
            }
        }
        return amount;
    }

    private Subscription getOwned(UUID subscriptionId, UUID ownerId) {
        return repository.findById(subscriptionId)
                .filter(s -> s.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));
    }
}
