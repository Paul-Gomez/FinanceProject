package com.fincore.analytics.infrastructure.web;

import com.fincore.analytics.application.AnalyticsService;
import com.fincore.analytics.application.AnalyticsService.CategoryRow;
import com.fincore.analytics.application.AnalyticsService.ForecastMonth;
import com.fincore.analytics.application.AnalyticsService.MonthComparison;
import com.fincore.analytics.application.AnalyticsService.MonthRow;
import com.fincore.analytics.application.AnalyticsService.Summary;
import com.fincore.analytics.infrastructure.AnalyticsQueryRepository.MerchantTotal;
import com.fincore.analytics.infrastructure.AnalyticsQueryRepository.TopExpense;
import com.fincore.identity.infrastructure.security.CurrentUserId;
import com.fincore.shared.time.DateRange;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Si no se indican fechas se usa el mes actual; si no se indica moneda, la moneda base del usuario. */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    public enum FlowType { INCOME, EXPENSE }

    private final AnalyticsService analytics;
    private final Clock clock;

    public AnalyticsController(AnalyticsService analytics, Clock clock) {
        this.analytics = analytics;
        this.clock = clock;
    }

    @GetMapping("/summary")
    public Summary summary(@CurrentUserId UUID userId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(required = false) String currency) {
        return analytics.summary(userId, analytics.currencyFor(userId, currency), range(from, to));
    }

    @GetMapping("/monthly")
    public List<MonthRow> monthly(@CurrentUserId UUID userId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) String currency) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : YearMonth.from(end).minusMonths(11).atDay(1);
        return analytics.monthly(userId, analytics.currencyFor(userId, currency), AnalyticsService.range(start, end));
    }

    @GetMapping("/categories")
    public List<CategoryRow> byCategory(@CurrentUserId UUID userId,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) String currency,
                                        @RequestParam(defaultValue = "EXPENSE") FlowType type,
                                        @RequestParam(defaultValue = "false") boolean rollUp) {
        return analytics.byCategory(userId, analytics.currencyFor(userId, currency), range(from, to), type.name(), rollUp);
    }

    @GetMapping("/top-expenses")
    public List<TopExpense> topExpenses(@CurrentUserId UUID userId,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) String currency,
                                        @RequestParam(defaultValue = "10") int limit) {
        return analytics.topExpenses(userId, analytics.currencyFor(userId, currency), range(from, to), limit);
    }

    @GetMapping("/top-merchants")
    public List<MerchantTotal> topMerchants(@CurrentUserId UUID userId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(required = false) String currency,
                                            @RequestParam(defaultValue = "10") int limit) {
        return analytics.topMerchants(userId, analytics.currencyFor(userId, currency), range(from, to), limit);
    }

    /** month en formato 2026-10; por defecto el mes actual. */
    @GetMapping("/compare")
    public MonthComparison compare(@CurrentUserId UUID userId,
                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                   @RequestParam(required = false) String currency) {
        return analytics.compare(userId, analytics.currencyFor(userId, currency),
                month != null ? month : YearMonth.now(clock));
    }

    @GetMapping("/recurring-forecast")
    public List<ForecastMonth> recurringForecast(@CurrentUserId UUID userId,
                                                 @RequestParam(defaultValue = "3") int months,
                                                 @RequestParam(required = false) String currency) {
        return analytics.recurringForecast(userId, analytics.currencyFor(userId, currency), months);
    }

    private DateRange range(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            YearMonth month = YearMonth.now(clock);
            return new DateRange(month.atDay(1), month.atEndOfMonth());
        }
        return AnalyticsService.range(from, to);
    }
}
