package com.fincore.categories.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryRuleIT extends IntegrationTest {

    private Tokens user;
    private UUID accountId;
    private Map<String, UUID> system;

    @BeforeEach
    void setUp() throws Exception {
        user = registerAndLogin("tests");
        accountId = id(perform(post("/api/v1/accounts"), Map.of("name", "Cuenta", "type", "BANK", "currencyCode", "EUR"))
                .andExpect(status().isCreated()));
        system = new HashMap<>();
        String body = mockMvc.perform(get("/api/v1/categories").header("Authorization", user.bearer()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (JsonNode c : objectMapper.readTree(body)) {
            if (c.get("system").asBoolean()) {
                system.put(c.get("name").asText(), UUID.fromString(c.get("id").asText()));
            }
        }
    }

    @Test
    void movementWithoutCategoryIsCategorizedBySystemRules() throws Exception {
        expense("Compra MERCADONA Pº Castellana")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(system.get("Alimentación").toString()));
        expense("Algo que no conoce ninguna regla")
                .andExpect(jsonPath("$.categoryId").doesNotExist());
    }

    @Test
    void userRulesOverrideSystemRules() throws Exception {
        UUID mine = id(perform(post("/api/v1/categories"),
                Map.of("name", "Súper del barrio", "kind", "EXPENSE")).andExpect(status().isCreated()));
        perform(post("/api/v1/category-rules"), Map.of("pattern", "mercadona", "categoryId", mine, "priority", 0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pattern").value("MERCADONA"));

        expense("MERCADONA 123").andExpect(jsonPath("$.categoryId").value(mine.toString()));
    }

    @Test
    void incomeIsNotPutInAnExpenseCategory() throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("accountId", accountId, "type", "INCOME",
                "amount", "9.99", "currencyCode", "EUR", "description", "Devolución NETFLIX"));
        perform(post("/api/v1/transactions"), body).andExpect(jsonPath("$.categoryId").doesNotExist());
    }

    @Test
    void testEndpointShowsWhichRuleWouldApply() throws Exception {
        perform(post("/api/v1/category-rules/test"), Map.of("description", "uber trip madrid", "kind", "EXPENSE"))
                .andExpect(jsonPath("$.categoryId").value(system.get("Transporte").toString()))
                .andExpect(jsonPath("$.matchedPattern").value("UBER"));
    }

    @Test
    void rejectsTooShortPatternsAndForeignRules() throws Exception {
        perform(post("/api/v1/category-rules"), Map.of("pattern", "de", "categoryId", system.get("Ocio"), "priority", 0))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY_RULE"));

        UUID ruleId = id(perform(post("/api/v1/category-rules"),
                Map.of("pattern", "cine yelmo", "categoryId", system.get("Ocio"), "priority", 0)));
        Tokens other = registerAndLogin("otro");
        mockMvc.perform(delete("/api/v1/category-rules/" + ruleId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/category-rules/" + ruleId).header("Authorization", user.bearer()))
                .andExpect(status().isNoContent());
    }

    private ResultActions expense(String description) throws Exception {
        return perform(post("/api/v1/transactions"), Map.of("accountId", accountId, "type", "EXPENSE",
                "amount", "12.30", "currencyCode", "EUR", "description", description));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Map<String, ?> body) throws Exception {
        return mockMvc.perform(request.header("Authorization", user.bearer())
                .contentType("application/json").content(objectMapper.writeValueAsString(body)));
    }

    private UUID id(ResultActions result) throws Exception {
        return UUID.fromString(objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText());
    }
}
