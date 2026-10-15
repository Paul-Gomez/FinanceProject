package com.fincore.exchangerates.infrastructure.web;

import com.fincore.exchangerates.application.ExchangeRateService;
import com.fincore.exchangerates.application.ExchangeRateService.ExchangeRate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    public ExchangeRateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    public record ExchangeRateRequest(
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String baseCurrency,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String quoteCurrency,
            @NotNull LocalDate date,
            @NotNull @Positive BigDecimal rate,
            @Size(max = 50) String source) {
    }

    /** Solo administradores (ver SecurityConfig: /api/v1/admin/** requiere ROLE_ADMIN). */
    @PutMapping("/api/v1/admin/exchange-rates")
    public ResponseEntity<Void> upsert(@Valid @RequestBody ExchangeRateRequest request) {
        exchangeRateService.save(request.baseCurrency(), request.quoteCurrency(), request.date(), request.rate(),
                request.source());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/exchange-rates")
    public List<ExchangeRate> history(@RequestParam String base, @RequestParam String quote) {
        return exchangeRateService.history(base, quote);
    }
}
