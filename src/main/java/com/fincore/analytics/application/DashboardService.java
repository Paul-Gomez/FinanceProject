package com.fincore.analytics.application;

import com.fincore.accounts.application.AccountService;
import com.fincore.accounts.domain.Account;
import com.fincore.accounts.domain.AccountStatus;
import com.fincore.bills.application.BillService;
import com.fincore.bills.domain.Bill;
import com.fincore.bills.domain.BillStatus;
import com.fincore.budgets.application.BudgetService;
import com.fincore.categories.application.CategoryQueryService;
import com.fincore.exchangerates.application.ExchangeRateService;
import com.fincore.goals.application.GoalService;
import com.fincore.goals.domain.GoalStatus;
import com.fincore.shared.money.Money;
import com.fincore.shared.time.DateRange;
import com.fincore.subscriptions.application.SubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int UPCOMING_DAYS = 30;
    static final int MAX_UPCOMING = 20;

    public enum PeriodType { MONTH, QUARTER, YEAR, CUSTOM }

    public record Balance(String currency, BigDecimal total, int accounts) {
    }

    public record TotalBalance(String currency, BigDecimal amount, List<String> missingRates) {
    }

    public record BudgetUsage(UUID budgetId, UUID categoryId, String categoryName, String currency, BigDecimal budgeted,
                              BigDecimal spent, BigDecimal percentageUsed, boolean exceeded) {
    }

    public record UpcomingPayment(String type, UUID id, String name, LocalDate date, BigDecimal amount, String currency,
                                  boolean overdue) {
    }

    public record GoalSummary(UUID goalId, String name, String currency, BigDecimal targetAmount, BigDecimal savedAmount,
                              BigDecimal progressPercentage, LocalDate targetDate) {
    }

    public record Dashboard(PeriodType periodType, LocalDate from, LocalDate to, String currency, List<Balance> balances,
                            TotalBalance totalBalance, BigDecimal income, BigDecimal expenses, BigDecimal savings,
                            BigDecimal savingsRate, List<BudgetUsage> budgets, List<UpcomingPayment> upcomingPayments,
                            List<GoalSummary> goals) {
    }

    private final AnalyticsService analytics;
    private final AccountService accountService;
    private final ExchangeRateService exchangeRates;
    private final BudgetService budgetService;
    private final CategoryQueryService categoryQueryService;
    private final BillService billService;
    private final SubscriptionService subscriptionService;
    private final GoalService goalService;
    private final Clock clock;

    public DashboardService(AnalyticsService analytics, AccountService accountService, ExchangeRateService exchangeRates,
                            BudgetService budgetService, CategoryQueryService categoryQueryService,
                            BillService billService, SubscriptionService subscriptionService, GoalService goalService,
                            Clock clock) {
        this.analytics = analytics;
        this.accountService = accountService;
        this.exchangeRates = exchangeRates;
        this.budgetService = budgetService;
        this.categoryQueryService = categoryQueryService;
        this.billService = billService;
        this.subscriptionService = subscriptionService;
        this.goalService = goalService;
        this.clock = clock;
    }

    public static DateRange periodRange(PeriodType type, LocalDate date, LocalDate from, LocalDate to) {
        return switch (type) {
            case MONTH -> new DateRange(YearMonth.from(date).atDay(1), YearMonth.from(date).atEndOfMonth());
            case QUARTER -> {
                YearMonth firstMonth = YearMonth.of(date.getYear(), ((date.getMonthValue() - 1) / 3) * 3 + 1);
                yield new DateRange(firstMonth.atDay(1), firstMonth.plusMonths(2).atEndOfMonth());
            }
            case YEAR -> new DateRange(date.withDayOfYear(1), date.withDayOfYear(date.lengthOfYear()));
            case CUSTOM -> AnalyticsService.range(from, to);
        };
    }

    public Dashboard build(UUID owner, PeriodType type, LocalDate date, LocalDate from, LocalDate to, String currency) {
        LocalDate today = LocalDate.now(clock);
        LocalDate reference = date != null ? date : today;
        DateRange range = periodRange(type, reference, from, to);
        String flowCurrency = analytics.currencyFor(owner, currency);

        List<Balance> balances = balances(owner);
        AnalyticsService.Summary summary = analytics.summary(owner, flowCurrency, range);

        return new Dashboard(type, range.from(), range.to(), flowCurrency, balances,
                totalBalance(balances, Currency.getInstance(flowCurrency), today),
                summary.income(), summary.expenses(), summary.savings(), summary.savingsRate(),
                budgets(owner, reference), upcomingPayments(owner, today), goals(owner));
    }

    /** Saldo por moneda de las cuentas no cerradas (las archivadas siguen teniendo dinero). */
    private List<Balance> balances(UUID owner) {
        Map<String, BigDecimal> totals = new TreeMap<>();
        Map<String, Integer> counts = new TreeMap<>();
        for (Account account : accountService.listByOwner(owner)) {
            if (account.getStatus() == AccountStatus.CLOSED) {
                continue;
            }
            String code = account.balance().currency().getCurrencyCode();
            totals.merge(code, account.balance().amount(), BigDecimal::add);
            counts.merge(code, 1, Integer::sum);
        }
        List<Balance> result = new ArrayList<>();
        totals.forEach((code, total) -> result.add(new Balance(code, total, counts.get(code))));
        return result;
    }

    /**
     * Suma de todos los saldos en una moneda. Si falta el tipo de cambio de alguna moneda no se da
     * un total parcial (sería engañoso): amount = null y se indica qué tipos faltan.
     */
    private TotalBalance totalBalance(List<Balance> balances, Currency target, LocalDate today) {
        BigDecimal sum = BigDecimal.ZERO;
        List<String> missing = new ArrayList<>();
        for (Balance balance : balances) {
            Currency source = Currency.getInstance(balance.currency());
            var converted = exchangeRates.tryConvert(Money.of(balance.total(), source), target, today);
            if (converted.isPresent()) {
                sum = sum.add(converted.get().amount());
            } else {
                missing.add(balance.currency());
            }
        }
        return new TotalBalance(target.getCurrencyCode(), missing.isEmpty() ? Money.of(sum, target).amount() : null, missing);
    }

    private List<BudgetUsage> budgets(UUID owner, LocalDate reference) {
        return budgetService.listStatus(owner, reference).stream()
                .map(view -> new BudgetUsage(view.budget().getId(), view.budget().getCategoryId(),
                        categoryQueryService.nameOf(view.budget().getCategoryId()), view.budget().getCurrencyCode(),
                        view.status().budgeted().amount(), view.status().spent().amount(),
                        view.status().percentageUsed(), view.status().exceeded()))
                .sorted(Comparator.comparing(BudgetUsage::percentageUsed).reversed())
                .toList();
    }

    private List<UpcomingPayment> upcomingPayments(UUID owner, LocalDate today) {
        List<UpcomingPayment> payments = new ArrayList<>();
        for (Bill bill : billService.openDueUntil(owner, today.plusDays(UPCOMING_DAYS))) {
            payments.add(new UpcomingPayment("BILL", bill.getId(), bill.getProvider(), bill.getDueDate(),
                    bill.amount().amount(), bill.getCurrencyCode(), bill.getStatus() == BillStatus.OVERDUE));
        }
        for (var charge : subscriptionService.chargesBetween(owner, today, today.plusDays(UPCOMING_DAYS))) {
            payments.add(new UpcomingPayment("SUBSCRIPTION", charge.subscriptionId(), charge.name(), charge.date(),
                    charge.amount().amount(), charge.amount().currency().getCurrencyCode(), false));
        }
        return payments.stream()
                .sorted(Comparator.comparing(UpcomingPayment::date).thenComparing(UpcomingPayment::name))
                .limit(MAX_UPCOMING)
                .toList();
    }

    private List<GoalSummary> goals(UUID owner) {
        return goalService.list(owner).stream()
                .filter(view -> view.goal().getStatus() == GoalStatus.ACTIVE)
                .map(view -> new GoalSummary(view.goal().getId(), view.goal().getName(), view.goal().getCurrencyCode(),
                        view.goal().target().amount(), view.progress().saved().amount(),
                        view.progress().percentage().setScale(2, RoundingMode.HALF_UP), view.goal().getTargetDate()))
                .toList();
    }
}
