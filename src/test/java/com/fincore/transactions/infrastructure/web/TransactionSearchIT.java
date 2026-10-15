package com.fincore.transactions.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionSearchIT extends IntegrationTest {

    private Tokens user;
    private UUID bank;
    private UUID cash;
    private UUID food;
    private UUID supermarket;

    @BeforeEach
    void setUp() throws Exception {
        user = registerAndLogin("tests");
        bank = id(post("/api/v1/accounts", Map.of("name", "Banco", "type", "BANK", "currencyCode", "EUR")));
        cash = id(post("/api/v1/accounts", Map.of("name", "Efectivo", "type", "CASH", "currencyCode", "EUR")));
        food = systemCategory("Alimentación");
        supermarket = id(post("/api/v1/categories", Map.of("name", "Super", "kind", "EXPENSE", "parentId", food)));

        movement(bank, "INCOME", "1500.00", "2026-09-01", "Nómina septiembre", null);
        movement(bank, "EXPENSE", "60.00", "2026-09-03", "Mercado central", food);
        movement(bank, "EXPENSE", "25.50", "2026-09-10", "Hiper 100% ahorro", supermarket);
        movement(cash, "EXPENSE", "4.20", "2026-09-12", "Café", null);
        movement(cash, "EXPENSE", "120.00", "2026-10-02", "Zapatillas", null);
    }

    @Test
    void listsAllAccountsOfTheUserByDefault() throws Exception {
        assertThat(descriptions(Map.of())).containsExactly(
                "Zapatillas", "Café", "Hiper 100% ahorro", "Mercado central", "Nómina septiembre");
    }

    @Test
    void filtersCombine() throws Exception {
        assertThat(descriptions(Map.of("accountId", cash.toString()))).containsExactly("Zapatillas", "Café");
        assertThat(descriptions(Map.of("from", "2026-09-02", "to", "2026-09-30")))
                .containsExactly("Café", "Hiper 100% ahorro", "Mercado central");
        assertThat(descriptions(Map.of("type", "INCOME"))).containsExactly("Nómina septiembre");
        assertThat(descriptions(Map.of("minAmount", "20", "maxAmount", "100")))
                .containsExactly("Hiper 100% ahorro", "Mercado central");
    }

    @Test
    void categoryFilterIncludesSubcategoriesUnlessAskedNotTo() throws Exception {
        assertThat(descriptions(Map.of("categoryId", food.toString())))
                .containsExactly("Hiper 100% ahorro", "Mercado central");
        assertThat(descriptions(Map.of("categoryId", food.toString(), "includeSubcategories", "false")))
                .containsExactly("Mercado central");
    }

    @Test
    void textSearchIgnoresCaseAndTreatsWildcardsLiterally() throws Exception {
        assertThat(descriptions(Map.of("q", "MERCADO"))).containsExactly("Mercado central");
        assertThat(descriptions(Map.of("q", "100%"))).containsExactly("Hiper 100% ahorro");
        assertThat(descriptions(Map.of("q", "%"))).containsExactly("Hiper 100% ahorro");
    }

    @Test
    void sortsByAmountAndPaginates() throws Exception {
        assertThat(descriptions(Map.of("sort", "AMOUNT", "direction", "ASC")))
                .containsExactly("Zapatillas", "Mercado central", "Hiper 100% ahorro", "Café", "Nómina septiembre");

        JsonNode page = list(Map.of("size", "2", "page", "1"));
        assertThat(page.get("totalElements").asInt()).isEqualTo(5);
        assertThat(page.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page.get("content").get(0).get("description").asText()).isEqualTo("Hiper 100% ahorro");
    }

    @Test
    void otherUsersOnlySeeTheirOwn() throws Exception {
        Tokens other = registerAndLogin("otro");

        var request = MockMvcRequestBuilders.get("/api/v1/transactions").header("Authorization", other.bearer());
        assertThat(objectMapper.readTree(mockMvc.perform(request).andReturn().getResponse().getContentAsString())
                .get("totalElements").asInt()).isZero();
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/transactions").param("accountId", bank.toString())
                        .header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
    }

    private List<String> descriptions(Map<String, String> params) throws Exception {
        List<String> result = new ArrayList<>();
        list(params).get("content").forEach(t -> result.add(t.get("description").asText()));
        return result;
    }

    private JsonNode list(Map<String, String> params) throws Exception {
        var request = MockMvcRequestBuilders.get("/api/v1/transactions").header("Authorization", user.bearer());
        params.forEach(request::param);
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void movement(UUID account, String type, String amount, String date, String description,
                          UUID categoryId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("accountId", account, "type", type, "amount", amount,
                "currencyCode", "EUR", "date", date, "description", description));
        if (categoryId != null) {
            body.put("categoryId", categoryId);
        }
        assertThat(post("/api/v1/transactions", body).get("id")).isNotNull();
    }

    private JsonNode post(String url, Map<String, ?> body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(MockMvcRequestBuilders.post(url)
                        .header("Authorization", user.bearer())
                        .contentType("application/json").content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
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
