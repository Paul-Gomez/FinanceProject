package com.fincore.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.exchangerates.application.ExchangeRateService;
import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardIT extends IntegrationTest {

    @Autowired
    private ExchangeRateService exchangeRates;

    private Tokens user;
    private UUID eurAccount;
    private UUID leisure;

    @BeforeEach
    void setUp() throws Exception {
        user = registerAndLogin("tests");
        leisure = systemCategory("Ocio");
        eurAccount = account("Banco", "EUR", "1000.00");
    }

    @Test
    void dashboardBringsEverythingTogether() throws Exception {
        LocalDate today = LocalDate.now();
        account("Libras", "GBP", "400.00");
        // 1 EUR = 0.80 GBP, así que 400 GBP = 500 EUR (se usa el inverso). En EUR: 1000 + 2000 - 100 = 2900.
        exchangeRates.save("EUR", "GBP", today, new BigDecimal("0.80"), "test");

        post("/api/v1/transactions", Map.of("accountId", eurAccount, "type", "INCOME", "amount", "2000.00",
                "currencyCode", "EUR", "date", today.toString()));
        post("/api/v1/transactions", Map.of("accountId", eurAccount, "type", "EXPENSE", "amount", "100.00",
                "currencyCode", "EUR", "date", today.toString(), "categoryId", leisure));
        post("/api/v1/budgets", Map.of("categoryId", leisure, "amount", "200.00", "currencyCode", "EUR", "period", "MONTHLY"));
        post("/api/v1/bills", Map.of("provider", "Luz", "amount", "45.00", "currencyCode", "EUR",
                "issueDate", today.toString(), "dueDate", today.plusDays(5).toString()));
        post("/api/v1/subscriptions", Map.of("name", "Netflix", "amount", "12.99", "currencyCode", "EUR",
                "billingCycle", "MONTHLY", "startDate", today.plusDays(3).toString()));
        post("/api/v1/goals", Map.of("name", "Viaje", "targetAmount", "1000.00", "currencyCode", "EUR"));

        JsonNode dashboard = dashboard(Map.of("period", "MONTH"));

        assertThat(dashboard.get("balances").size()).isEqualTo(2);
        assertThat(dashboard.at("/totalBalance/amount").decimalValue()).isEqualByComparingTo("3400.00");
        assertThat(dashboard.at("/totalBalance/missingRates").size()).isZero();
        assertThat(dashboard.get("income").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(dashboard.get("expenses").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(dashboard.get("savingsRate").decimalValue()).isEqualByComparingTo("95.00");
        assertThat(dashboard.at("/budgets/0/categoryName").asText()).isEqualTo("Ocio");
        assertThat(dashboard.at("/budgets/0/percentageUsed").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(dashboard.at("/upcomingPayments/0/name").asText()).isEqualTo("Netflix");
        assertThat(dashboard.at("/upcomingPayments/1/name").asText()).isEqualTo("Luz");
        assertThat(dashboard.at("/goals/0/name").asText()).isEqualTo("Viaje");
    }

    @Test
    void totalIsNotGivenWhenARateIsMissing() throws Exception {
        account("Yenes", "JPY", "10000");

        JsonNode dashboard = dashboard(Map.of());

        assertThat(dashboard.at("/totalBalance/amount").isNull()).isTrue();
        assertThat(dashboard.at("/totalBalance/missingRates/0").asText()).isEqualTo("JPY");
    }

    @Test
    void customPeriodNeedsDates() throws Exception {
        var request = MockMvcRequestBuilders.get("/api/v1/dashboard").param("period", "CUSTOM")
                .header("Authorization", user.bearer());
        mockMvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));

        JsonNode custom = dashboard(Map.of("period", "CUSTOM", "from", "2026-01-01", "to", "2026-03-31"));
        assertThat(custom.get("from").asText()).isEqualTo("2026-01-01");
        assertThat(custom.get("to").asText()).isEqualTo("2026-03-31");
    }

    private JsonNode dashboard(Map<String, String> params) throws Exception {
        var request = MockMvcRequestBuilders.get("/api/v1/dashboard").header("Authorization", user.bearer());
        params.forEach(request::param);
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode post(String url, Map<String, ?> body) throws Exception {
        String response = mockMvc.perform(MockMvcRequestBuilders.post(url).header("Authorization", user.bearer())
                        .contentType("application/json").content(objectMapper.writeValueAsString(new HashMap<>(body))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isEmpty() ? null : objectMapper.readTree(response);
    }

    private UUID account(String name, String currency, String balance) throws Exception {
        return UUID.fromString(post("/api/v1/accounts", Map.of("name", name, "type", "BANK", "currencyCode", currency,
                "initialBalance", balance)).get("id").asText());
    }

    private UUID systemCategory(String name) throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/categories").header("Authorization", user.bearer()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (JsonNode c : objectMapper.readTree(body)) {
            if (c.get("system").asBoolean() && c.get("name").asText().equals(name)) {
                return UUID.fromString(c.get("id").asText());
            }
        }
        throw new IllegalStateException(name);
    }
}
