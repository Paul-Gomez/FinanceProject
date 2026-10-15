package com.fincore.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Julio: nómina 2000, Mercadona 300 (Alimentación), Lidl 100 (Alimentación > Supermercado).
 * Agosto: nómina 2000, Mercadona 500, cine 50 (Ocio), kiosco 10 (sin categoría).
 * Además, cosas que NO deben contar: saldo inicial (ajuste), una transferencia entre cuentas
 * propias y un gasto en una cuenta en USD.
 */
class AnalyticsIT extends IntegrationTest {

    private Tokens user;
    private Map<String, UUID> categories;
    private UUID eur;

    @BeforeEach
    void setUp() throws Exception {
        user = registerAndLogin("tests");
        categories = systemCategories();
        UUID supermarket = id(post("/api/v1/categories", Map.of("name", "Supermercado", "kind", "EXPENSE",
                "parentId", categories.get("Alimentación"))));
        eur = account("Banco", "EUR", "1000.00");
        UUID savings = account("Ahorro", "EUR", "0.00");
        UUID usd = account("Dólares", "USD", "5000.00");

        movement(eur, "EUR", "INCOME", "2000.00", "2026-07-05", "Nómina", categories.get("Nómina"));
        movement(eur, "EUR", "EXPENSE", "300.00", "2026-07-10", "MERCADONA", categories.get("Alimentación"));
        movement(eur, "EUR", "EXPENSE", "100.00", "2026-07-12", "LIDL", supermarket);
        movement(eur, "EUR", "INCOME", "2000.00", "2026-08-05", "Nómina", categories.get("Nómina"));
        movement(eur, "EUR", "EXPENSE", "500.00", "2026-08-10", "MERCADONA", categories.get("Alimentación"));
        movement(eur, "EUR", "EXPENSE", "50.00", "2026-08-20", "CINE", categories.get("Ocio"));
        movement(eur, "EUR", "EXPENSE", "10.00", "2026-08-21", "kiosco", null);
        movement(usd, "USD", "EXPENSE", "1000.00", "2026-08-15", "Hotel", categories.get("Ocio"));
        post("/api/v1/transfers", Map.of("fromAccountId", eur, "toAccountId", savings, "amount", "200.00",
                "currencyCode", "EUR", "date", "2026-08-25"));
    }

    @Test
    void summaryIgnoresTransfersAdjustmentsAndOtherCurrencies() throws Exception {
        get("/api/v1/analytics/summary", Map.of("from", "2026-07-01", "to", "2026-08-31"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.income").value(4000.00))
                .andExpect(jsonPath("$.expenses").value(960.00))
                .andExpect(jsonPath("$.savings").value(3040.00))
                .andExpect(jsonPath("$.savingsRate").value(76.00))
                .andExpect(jsonPath("$.averageDailyExpense").value(15.48))
                .andExpect(jsonPath("$.movements").value(7));

        get("/api/v1/analytics/summary", Map.of("from", "2026-08-01", "to", "2026-08-31", "currency", "USD"))
                .andExpect(jsonPath("$.expenses").value(1000.00))
                .andExpect(jsonPath("$.savingsRate").doesNotExist());
    }

    @Test
    void monthlyIncludesEmptyMonths() throws Exception {
        get("/api/v1/analytics/monthly", Map.of("from", "2026-06-01", "to", "2026-08-31"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].month").value("2026-06"))
                .andExpect(jsonPath("$[0].expenses").value(0.00))
                .andExpect(jsonPath("$[1].expenses").value(400.00))
                .andExpect(jsonPath("$[2].income").value(2000.00))
                .andExpect(jsonPath("$[2].expenses").value(560.00))
                .andExpect(jsonPath("$[2].savings").value(1440.00));
    }

    @Test
    void categoriesWithAndWithoutRollingUpSubcategories() throws Exception {
        JsonNode august = json(get("/api/v1/analytics/categories", Map.of("from", "2026-08-01", "to", "2026-08-31",
                "rollUp", "true")));
        assertThat(august.get(0).get("name").asText()).isEqualTo("Alimentación");
        assertThat(august.get(0).get("percentage").decimalValue()).isEqualByComparingTo("89.29");
        assertThat(august.get(2).get("name").asText()).isEqualTo("Sin categoría");

        JsonNode julyFlat = json(get("/api/v1/analytics/categories", Map.of("from", "2026-07-01", "to", "2026-07-31")));
        assertThat(julyFlat.size()).isEqualTo(2);
        JsonNode julyRolled = json(get("/api/v1/analytics/categories", Map.of("from", "2026-07-01", "to", "2026-07-31",
                "rollUp", "true")));
        assertThat(julyRolled.size()).isEqualTo(1);
        assertThat(julyRolled.get(0).get("total").decimalValue()).isEqualByComparingTo("400.00");
    }

    @Test
    void topExpensesAndMerchants() throws Exception {
        get("/api/v1/analytics/top-expenses", Map.of("from", "2026-08-01", "to", "2026-08-31", "limit", "2"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].amount").value(500.00))
                .andExpect(jsonPath("$[1].description").value("CINE"));

        get("/api/v1/analytics/top-merchants", Map.of("from", "2026-07-01", "to", "2026-08-31"))
                .andExpect(jsonPath("$[0].merchant").value("MERCADONA"))
                .andExpect(jsonPath("$[0].movements").value(2))
                .andExpect(jsonPath("$[0].total").value(800.00));
    }

    @Test
    void comparesAMonthWithThePreviousOne() throws Exception {
        JsonNode comparison = json(get("/api/v1/analytics/compare", Map.of("month", "2026-08")));

        assertThat(comparison.get("expenses").decimalValue()).isEqualByComparingTo("560.00");
        assertThat(comparison.get("previousExpenses").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(comparison.get("difference").decimalValue()).isEqualByComparingTo("160.00");
        JsonNode food = comparison.get("categories").get(0);
        assertThat(food.get("name").asText()).isEqualTo("Alimentación");
        assertThat(food.get("changePercentage").decimalValue()).isEqualByComparingTo("25.00");
    }

    @Test
    void forecastAddsSubscriptionsAndOpenBills() throws Exception {
        LocalDate today = LocalDate.now();
        post("/api/v1/subscriptions", Map.of("name", "Spotify", "amount", "10.00", "currencyCode", "EUR",
                "billingCycle", "MONTHLY", "startDate", today.toString()));
        post("/api/v1/bills", Map.of("provider", "Agua", "amount", "50.00", "currencyCode", "EUR",
                "issueDate", today.minusDays(10).toString(), "dueDate", today.minusDays(1).toString()));

        get("/api/v1/analytics/recurring-forecast", Map.of("months", "2"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].subscriptions").value(10.00))
                .andExpect(jsonPath("$[0].bills").value(50.00))
                .andExpect(jsonPath("$[0].total").value(60.00))
                .andExpect(jsonPath("$[1].total").value(10.00));
    }

    @Test
    void rejectsInvalidRanges() throws Exception {
        get("/api/v1/analytics/summary", Map.of("from", "2026-08-31", "to", "2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        get("/api/v1/analytics/summary", Map.of("from", "2010-01-01", "to", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions get(String url, Map<String, String> params) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .header("Authorization", user.bearer());
        params.forEach(request::param);
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode post(String url, Map<String, ?> body) throws Exception {
        String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                        .header("Authorization", user.bearer())
                        .contentType("application/json").content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isEmpty() ? null : objectMapper.readTree(response);
    }

    private UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }

    private UUID account(String name, String currency, String balance) throws Exception {
        return id(post("/api/v1/accounts", Map.of("name", name, "type", "BANK", "currencyCode", currency,
                "initialBalance", balance)));
    }

    private void movement(UUID account, String currency, String type, String amount, String date,
                          String description, UUID categoryId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("accountId", account, "type", type, "amount", amount,
                "currencyCode", currency, "date", date, "description", description));
        if (categoryId != null) {
            body.put("categoryId", categoryId);
        }
        JsonNode created = post("/api/v1/transactions", body);
        assertThat(created.get("id")).as("movimiento " + description).isNotNull();
    }

    private Map<String, UUID> systemCategories() throws Exception {
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/categories").header("Authorization", user.bearer()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, UUID> byName = new HashMap<>();
        for (JsonNode c : objectMapper.readTree(body)) {
            if (c.get("system").asBoolean()) {
                byName.put(c.get("name").asText(), UUID.fromString(c.get("id").asText()));
            }
        }
        return byName;
    }
}
