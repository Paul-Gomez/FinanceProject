package com.fincore.subscriptions.infrastructure.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionIT extends IntegrationTest {

    private Tokens user;

    @BeforeEach
    void setUp() throws Exception {
        user = registerAndLogin("tests");
    }

    @Test
    void summaryAddsUpMonthlyAndYearlyEquivalents() throws Exception {
        LocalDate start = LocalDate.now();
        create(Map.of("name", "Netflix", "amount", "12.99", "currencyCode", "EUR", "billingCycle", "MONTHLY",
                "startDate", start.toString())).andExpect(status().isCreated());
        create(Map.of("name", "Gimnasio", "amount", "120.00", "currencyCode", "EUR", "billingCycle", "QUARTERLY",
                "startDate", start.toString())).andExpect(status().isCreated());
        create(Map.of("name", "Periódico", "amount", "1.00", "currencyCode", "EUR", "billingCycle", "WEEKLY",
                "startDate", start.toString())).andExpect(status().isCreated());

        // 12.99*12 + 120*4 + 1*52 = 687.88 al año; 687.88 / 12 = 57.3233 -> 57.32 al mes
        mockMvc.perform(get("/api/v1/subscriptions/summary").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$[0].currencyCode").value("EUR"))
                .andExpect(jsonPath("$[0].subscriptions").value(3))
                .andExpect(jsonPath("$[0].yearly").value(687.88))
                .andExpect(jsonPath("$[0].monthly").value(57.32));
    }

    @Test
    void upcomingListsEveryChargeInTheWindow() throws Exception {
        LocalDate start = LocalDate.now().plusDays(1);
        create(Map.of("name", "Spotify", "amount", "10.99", "currencyCode", "EUR", "billingCycle", "MONTHLY",
                "startDate", start.toString()));
        create(Map.of("name", "Clase", "amount", "8.00", "currencyCode", "EUR", "billingCycle", "WEEKLY",
                "startDate", start.toString()));

        mockMvc.perform(get("/api/v1/subscriptions/upcoming").param("days", "20").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].date").value(start.toString()));
    }

    @Test
    void validatesCategoryKindAndAccountCurrency() throws Exception {
        UUID usdAccount = UUID.fromString(objectMapper.readTree(mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", user.bearer()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("name", "USD", "type", "BANK", "currencyCode", "USD"))))
                .andReturn().getResponse().getContentAsString()).get("id").asText());

        Map<String, Object> body = new HashMap<>(Map.of("name", "Netflix", "amount", "12.99", "currencyCode", "EUR",
                "billingCycle", "MONTHLY", "startDate", LocalDate.now().toString()));
        body.put("accountId", usdAccount);
        create(body).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));

        body.remove("accountId");
        body.put("categoryId", systemCategory("Nómina"));
        create(body).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CATEGORY_KIND_MISMATCH"));
    }

    @Test
    void othersSubscriptionsAreNotVisible() throws Exception {
        String id = objectMapper.readTree(create(Map.of("name", "HBO", "amount", "9.99", "currencyCode", "EUR",
                "billingCycle", "MONTHLY", "startDate", LocalDate.now().toString()))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        Tokens other = registerAndLogin("otro");

        mockMvc.perform(get("/api/v1/subscriptions/" + id).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
    }

    private ResultActions create(Map<String, ?> body) throws Exception {
        return mockMvc.perform(post("/api/v1/subscriptions").header("Authorization", user.bearer())
                .contentType("application/json").content(objectMapper.writeValueAsString(body)));
    }

    private UUID systemCategory(String name) throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories").header("Authorization", user.bearer()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (JsonNode c : objectMapper.readTree(body)) {
            if (c.get("system").asBoolean() && c.get("name").asText().equals(name)) {
                return UUID.fromString(c.get("id").asText());
            }
        }
        throw new IllegalStateException(name);
    }
}
