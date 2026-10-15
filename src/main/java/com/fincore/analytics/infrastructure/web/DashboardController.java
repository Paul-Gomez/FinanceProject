package com.fincore.analytics.infrastructure.web;

import com.fincore.analytics.application.DashboardService;
import com.fincore.analytics.application.DashboardService.Dashboard;
import com.fincore.analytics.application.DashboardService.PeriodType;
import com.fincore.identity.infrastructure.security.CurrentUserId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * period=MONTH|QUARTER|YEAR usa el periodo que contiene 'date' (por defecto hoy);
     * period=CUSTOM necesita 'from' y 'to'.
     */
    @GetMapping
    public Dashboard dashboard(@CurrentUserId UUID userId,
                               @RequestParam(defaultValue = "MONTH") PeriodType period,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) String currency) {
        return dashboardService.build(userId, period, date, from, to, currency);
    }
}
