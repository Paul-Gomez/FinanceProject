package com.fincore.budgets.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BudgetIT extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private Tokens user;
    private UUID accountId;
    private Map<String, UUID> categories;

    @BeforeEach
    void setUp() throws Exception {
        user = registerAndLogin("tests");
        accountId = createAccount("10000.00");
        categories = systemCategories();
    }

    @Test
    void spentIncludesSubcategoriesAndOnlyTheCurrentPeriod() throws Exception {
        UUID food = categories.get("Alimentación");
        UUID supermarket = createSubcategory("Supermercado", food);
        UUID budgetId = createBudget(food, "300.00");
        LocalDate today = LocalDate.now();

        expense("100.00", food, today);
        expense("50.00", supermarket, today);
        expense("30.00", categories.get("Ocio"), today);
        expense("20.00", food, today.minusMonths(1).withDayOfMonth(1));

        mockMvc.perform(get("/api/v1/budgets/" + budgetId).header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spent").value(150.00))
                .andExpect(jsonPath("$.remaining").value(150.00))
                .andExpect(jsonPath("$.percentageUsed").value(50.00))
                .andExpect(jsonPath("$.exceeded").value(false));
    }

    @Test
    void exceededBudgetShowsTheExcess() throws Exception {
        UUID leisure = categories.get("Ocio");
        createBudget(leisure, "150.00");
        expense("170.25", leisure, LocalDate.now());

        mockMvc.perform(get("/api/v1/budgets").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$[0].exceeded").value(true))
                .andExpect(jsonPath("$[0].excess").value(20.25))
                .andExpect(jsonPath("$[0].remaining").value(0.00));
    }

    @Test
    void rejectsIncomeCategoriesAndDuplicates() throws Exception {
        postBudget(categories.get("Nómina"), "100.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CATEGORY_KIND_MISMATCH"));

        UUID transport = categories.get("Transporte");
        UUID first = createBudget(transport, "100.00");
        postBudget(transport, "200.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUDGET_ALREADY_EXISTS"));

        mockMvc.perform(delete("/api/v1/budgets/" + first).header("Authorization", user.bearer()))
                .andExpect(status().isNoContent());
        postBudget(transport, "200.00").andExpect(status().isCreated());
    }

    @Test
    void someoneElsesBudgetIsNotFound() throws Exception {
        UUID budgetId = createBudget(categories.get("Salud"), "100.00");
        Tokens other = registerAndLogin("otro");

        mockMvc.perform(get("/api/v1/budgets/" + budgetId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void eachThresholdAlertFiresOnlyOncePerPeriod() throws Exception {
        UUID health = categories.get("Salud");
        UUID budgetId = createBudget(health, "100.00");
        LocalDate today = LocalDate.now();

        expense("79.99", health, today);
        assertThat(alerts(budgetId)).isEmpty();

        expense("0.01", health, today);
        assertThat(alerts(budgetId)).containsExactly(80);

        expense("20.00", health, today);
        assertThat(alerts(budgetId)).containsExactly(80, 100);

        expense("5.00", health, today);
        assertThat(alerts(budgetId)).containsExactly(80, 100);
    }

    @Test
    void concurrentExpensesDoNotDuplicateAlerts() throws Exception {
        UUID education = categories.get("Educación");
        UUID budgetId = createBudget(education, "100.00");

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tasks.add(() -> expense("10.00", education, LocalDate.now()));
        }
        pool.invokeAll(tasks);
        pool.shutdown();

        assertThat(alerts(budgetId)).containsExactly(80, 100);
    }

    private List<Integer> alerts(UUID budgetId) {
        return jdbc.queryForList("SELECT threshold FROM budget_alerts WHERE budget_id = ? ORDER BY threshold",
                Integer.class, budgetId);
    }

    private int expense(String amount, UUID categoryId, LocalDate date) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("accountId", accountId, "type", "EXPENSE",
                "amount", amount, "currencyCode", "EUR", "date", date.toString()));
        body.put("categoryId", categoryId);
        return mockMvc.perform(post("/api/v1/transactions").header("Authorization", user.bearer())
                        .contentType("application/json").content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getStatus();
    }

    private ResultActions postBudget(UUID categoryId, String amount) throws Exception {
        return mockMvc.perform(post("/api/v1/budgets").header("Authorization", user.bearer())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("categoryId", categoryId, "amount", amount,
                        "currencyCode", "EUR", "period", "MONTHLY"))));
    }

    private UUID createBudget(UUID categoryId, String amount) throws Exception {
        String body = postBudget(categoryId, amount).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID createAccount(String initialBalance) throws Exception {
        String body = mockMvc.perform(post("/api/v1/accounts").header("Authorization", user.bearer())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("name", "Cuenta", "type", "BANK",
                                "currencyCode", "EUR", "initialBalance", initialBalance))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID createSubcategory(String name, UUID parentId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/categories").header("Authorization", user.bearer())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "kind", "EXPENSE",
                                "parentId", parentId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private Map<String, UUID> systemCategories() throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories").header("Authorization", user.bearer()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, UUID> byName = new HashMap<>();
        for (JsonNode category : objectMapper.readTree(body)) {
            if (category.get("system").asBoolean()) {
                byName.put(category.get("name").asText(), UUID.fromString(category.get("id").asText()));
            }
        }
        return byName;
    }
}
