package com.fincore.analytics.application;

import com.fincore.analytics.infrastructure.AnalyticsQueryRepository;
import com.fincore.analytics.infrastructure.AnalyticsQueryRepository.CategoryTotal;
import com.fincore.analytics.infrastructure.AnalyticsQueryRepository.MerchantTotal;
import com.fincore.analytics.infrastructure.AnalyticsQueryRepository.MonthTotals;
import com.fincore.analytics.infrastructure.AnalyticsQueryRepository.TopExpense;
import com.fincore.analytics.infrastructure.AnalyticsQueryRepository.Totals;
import com.fincore.bills.application.BillService;
import com.fincore.bills.domain.Bill;
import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;
import com.fincore.shared.money.Money;
import com.fincore.shared.time.DateRange;
import com.fincore.subscriptions.application.SubscriptionService;
import com.fincore.subscriptions.application.SubscriptionService.UpcomingCharge;
import com.fincore.users.application.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    static final int MAX_RANGE_YEARS = 5;
    static final int MAX_LIMIT = 50;

    public record Summary(String currency, LocalDate from, LocalDate to, BigDecimal income, BigDecimal expenses,
                          BigDecimal savings, BigDecimal savingsRate, BigDecimal averageDailyExpense, long movements) {
    }

    public record MonthRow(YearMonth month, BigDecimal income, BigDecimal expenses, BigDecimal savings) {
    }

    public record CategoryRow(UUID categoryId, String name, String color, UUID parentId, BigDecimal total,
                              BigDecimal percentage, long movements) {
    }

    public record CategoryComparison(UUID categoryId, String name, BigDecimal current, BigDecimal previous,
                                     BigDecimal difference, BigDecimal changePercentage) {
    }

    public record MonthComparison(String currency, YearMonth month, YearMonth previousMonth, BigDecimal expenses,
                                  BigDecimal previousExpenses, BigDecimal difference, List<CategoryComparison> categories) {
    }

    public record ForecastMonth(YearMonth month, BigDecimal subscriptions, BigDecimal bills, BigDecimal total,
                                int payments) {
    }

    public static class InvalidDateRangeException extends BusinessException {
        public InvalidDateRangeException(String reason) {
            super(ErrorType.BAD_REQUEST, "INVALID_DATE_RANGE", "Rango de fechas no válido: " + reason);
        }
    }

    private final AnalyticsQueryRepository queries;
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final BillService billService;
    private final Clock clock;

    public AnalyticsService(AnalyticsQueryRepository queries, UserService userService,
                            SubscriptionService subscriptionService, BillService billService, Clock clock) {
        this.queries = queries;
        this.userService = userService;
        this.subscriptionService = subscriptionService;
        this.billService = billService;
        this.clock = clock;
    }

    public static DateRange range(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new InvalidDateRangeException("hacen falta 'from' y 'to'");
        }
        if (to.isBefore(from)) {
            throw new InvalidDateRangeException("'to' es anterior a 'from'");
        }
        if (from.plusYears(MAX_RANGE_YEARS).isBefore(to)) {
            throw new InvalidDateRangeException("como mucho " + MAX_RANGE_YEARS + " años");
        }
        return new DateRange(from, to);
    }

    /** Moneda en la que se calculan los totales: la pedida o, si no se indica, la base del usuario. */
    public String currencyFor(UUID owner, String requested) {
        return requested != null ? Money.currencyFor(requested).getCurrencyCode()
                : userService.baseCurrencyOf(owner).getCurrencyCode();
    }

    public Summary summary(UUID owner, String currency, DateRange range) {
        Totals totals = queries.totals(owner, currency, range);
        BigDecimal savings = totals.income().subtract(totals.expenses());
        long days = ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
        return new Summary(currency, range.from(), range.to(), scale(totals.income()), scale(totals.expenses()),
                scale(savings), percentage(savings, totals.income()),
                totals.expenses().divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP), totals.movements());
    }

    public List<MonthRow> monthly(UUID owner, String currency, DateRange range) {
        return queries.monthly(owner, currency, range).stream()
                .map(m -> new MonthRow(m.month(), scale(m.income()), scale(m.expenses()),
                        scale(m.income().subtract(m.expenses()))))
                .toList();
    }

    public List<CategoryRow> byCategory(UUID owner, String currency, DateRange range, String type, boolean rollUp) {
        List<CategoryTotal> totals = queries.byCategory(owner, currency, range, type, rollUp);
        BigDecimal grandTotal = totals.stream().map(CategoryTotal::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return totals.stream()
                .map(c -> new CategoryRow(c.categoryId(), c.categoryId() == null ? "Sin categoría" : c.name(), c.color(),
                        c.parentId(), scale(c.total()), percentage(c.total(), grandTotal), c.movements()))
                .toList();
    }

    public List<TopExpense> topExpenses(UUID owner, String currency, DateRange range, int limit) {
        return queries.topExpenses(owner, currency, range, clampLimit(limit));
    }

    public List<MerchantTotal> topMerchants(UUID owner, String currency, DateRange range, int limit) {
        return queries.topMerchants(owner, currency, range, clampLimit(limit));
    }

    /** Gasto de un mes frente al anterior, en total y por categoría (con subcategorías agrupadas). */
    public MonthComparison compare(UUID owner, String currency, YearMonth month) {
        YearMonth previous = month.minusMonths(1);
        DateRange current = new DateRange(month.atDay(1), month.atEndOfMonth());
        DateRange before = new DateRange(previous.atDay(1), previous.atEndOfMonth());

        Map<UUID, CategoryTotal> now = index(queries.byCategory(owner, currency, current, "EXPENSE", true));
        Map<UUID, CategoryTotal> then = index(queries.byCategory(owner, currency, before, "EXPENSE", true));
        Map<UUID, String> names = new LinkedHashMap<>();
        now.forEach((id, c) -> names.put(id, c.name()));
        then.forEach((id, c) -> names.putIfAbsent(id, c.name()));

        List<CategoryComparison> categories = new ArrayList<>();
        names.forEach((id, name) -> {
            BigDecimal a = now.containsKey(id) ? now.get(id).total() : BigDecimal.ZERO;
            BigDecimal b = then.containsKey(id) ? then.get(id).total() : BigDecimal.ZERO;
            categories.add(new CategoryComparison(id, id == null ? "Sin categoría" : name, scale(a), scale(b),
                    scale(a.subtract(b)), percentage(a.subtract(b), b)));
        });
        categories.sort((x, y) -> y.difference().abs().compareTo(x.difference().abs()));

        BigDecimal currentTotal = queries.totals(owner, currency, current).expenses();
        BigDecimal previousTotal = queries.totals(owner, currency, before).expenses();
        return new MonthComparison(currency, month, previous, scale(currentTotal), scale(previousTotal),
                scale(currentTotal.subtract(previousTotal)), categories);
    }

    /**
     * Previsión de pagos recurrentes de los próximos meses: cobros de suscripciones y facturas
     * abiertas. Las facturas ya vencidas y sin pagar se cuentan en el mes actual.
     */
    public List<ForecastMonth> recurringForecast(UUID owner, String currency, int months) {
        int horizon = Math.min(Math.max(months, 1), 12);
        LocalDate today = LocalDate.now(clock);
        YearMonth first = YearMonth.from(today);
        LocalDate end = first.plusMonths(horizon - 1L).atEndOfMonth();

        Map<YearMonth, BigDecimal[]> buckets = new LinkedHashMap<>();
        Map<YearMonth, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < horizon; i++) {
            buckets.put(first.plusMonths(i), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            counts.put(first.plusMonths(i), 0);
        }

        for (UpcomingCharge charge : subscriptionService.chargesBetween(owner, today, end)) {
            if (charge.amount().currency().getCurrencyCode().equals(currency)) {
                YearMonth month = YearMonth.from(charge.date());
                buckets.get(month)[0] = buckets.get(month)[0].add(charge.amount().amount());
                counts.merge(month, 1, Integer::sum);
            }
        }
        for (Bill bill : billService.openDueUntil(owner, end)) {
            if (bill.getCurrencyCode().equals(currency)) {
                YearMonth month = bill.getDueDate().isBefore(today) ? first : YearMonth.from(bill.getDueDate());
                buckets.get(month)[1] = buckets.get(month)[1].add(bill.amount().amount());
                counts.merge(month, 1, Integer::sum);
            }
        }

        List<ForecastMonth> forecast = new ArrayList<>();
        buckets.forEach((month, sums) -> forecast.add(new ForecastMonth(month, scale(sums[0]), scale(sums[1]),
                scale(sums[0].add(sums[1])), counts.get(month))));
        return forecast;
    }

    private static Map<UUID, CategoryTotal> index(List<CategoryTotal> totals) {
        Map<UUID, CategoryTotal> map = new LinkedHashMap<>();
        totals.forEach(t -> map.put(t.categoryId(), t));
        return map;
    }

    /** part / total * 100 con 2 decimales; null si total es 0 (no hay porcentaje que calcular). */
    static BigDecimal percentage(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }
}
