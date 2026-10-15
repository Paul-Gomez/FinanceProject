package com.fincore.transactions.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

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
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VoidAndRefundIT extends IntegrationTest {

    private Tokens user;
    private UUID account;
    private UUID leisure;

    @BeforeEach
    void setUp() throws Exception {
        user = registerAndLogin("tests");
        account = id(post("/api/v1/accounts", Map.of("name", "Banco", "type", "BANK", "currencyCode", "EUR",
                "initialBalance", "1000.00")));
        leisure = systemCategory("Ocio");
    }

    @Test
    void voidingAnExpenseRestoresTheBalanceAndHidesIt() throws Exception {
        UUID expense = expense("100.00");

        delete(expense).andExpect(status().isNoContent());

        assertBalance(account, "1000.00");
        get("/api/v1/transactions/" + expense).andExpect(jsonPath("$.status").value("VOIDED"));
        assertThat(list(Map.of("type", "EXPENSE")).get("totalElements").asInt()).isZero();
        assertThat(list(Map.of("type", "EXPENSE", "includeVoided", "true")).get("totalElements").asInt()).isEqualTo(1);
        delete(expense).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_OPERATION"));
    }

    @Test
    void voidingATransferLegVoidsBothLegs() throws Exception {
        UUID savings = id(post("/api/v1/accounts", Map.of("name", "Ahorro", "type", "SAVINGS", "currencyCode", "EUR")));
        JsonNode transfer = post("/api/v1/transfers", Map.of("fromAccountId", account, "toAccountId", savings,
                "amount", "300.00", "currencyCode", "EUR"));

        delete(UUID.fromString(transfer.at("/incoming/id").asText())).andExpect(status().isNoContent());

        assertBalance(account, "1000.00");
        assertBalance(savings, "0.00");
        get("/api/v1/transactions/" + transfer.at("/outgoing/id").asText()).andExpect(jsonPath("$.status").value("VOIDED"));
    }

    @Test
    void partialRefundsUpToTheExpense() throws Exception {
        UUID expense = expense("100.00");

        refund(expense, Map.of("amount", "30.00")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("REFUND"))
                .andExpect(jsonPath("$.amount").value(30.00))
                .andExpect(jsonPath("$.categoryId").value(leisure.toString()))
                .andExpect(jsonPath("$.refundedTransactionId").value(expense.toString()));
        refund(expense, Map.of("amount", "80.00")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REFUND_EXCEEDS_EXPENSE"));
        refund(expense, Map.of()).andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(70.00));
        refund(expense, Map.of()).andExpect(status().isUnprocessableEntity());

        assertBalance(account, "1000.00");
    }

    @Test
    void refundsReduceBudgetSpendingAndAnalytics() throws Exception {
        post("/api/v1/budgets", Map.of("categoryId", leisure, "amount", "200.00", "currencyCode", "EUR", "period", "MONTHLY"));
        UUID expense = expense("100.00");
        refund(expense, Map.of("amount", "30.00")).andExpect(status().isCreated());

        get("/api/v1/budgets").andExpect(jsonPath("$[0].spent").value(70.00));
        get("/api/v1/analytics/summary").andExpect(jsonPath("$.expenses").value(70.00));
        get("/api/v1/analytics/categories").andExpect(jsonPath("$[0].total").value(70.00));
    }

    @Test
    void anExpenseWithRefundsCannotBeVoidedUntilTheRefundsAre() throws Exception {
        UUID expense = expense("50.00");
        UUID refund = id(objectMapper.readTree(refund(expense, Map.of("amount", "20.00"))
                .andReturn().getResponse().getContentAsString()));

        delete(expense).andExpect(status().isConflict());
        delete(refund).andExpect(status().isNoContent());
        delete(expense).andExpect(status().isNoContent());

        assertBalance(account, "1000.00");
    }

    @Test
    void onlyExpensesCanBeRefunded() throws Exception {
        UUID income = id(post("/api/v1/transactions", Map.of("accountId", account, "type", "INCOME",
                "amount", "10.00", "currencyCode", "EUR")));

        refund(income, Map.of()).andExpect(status().isConflict());
    }

    @Test
    void concurrentRefundsNeverExceedTheExpense() throws Exception {
        UUID expense = expense("100.00");

        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> refund(expense, Map.of("amount", "60.00")).andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : pool.invokeAll(tasks)) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).containsOnlyOnce(201);
        assertBalance(account, "960.00");
    }

    @Test
    void othersCannotSeeVoidOrRefundMyMovements() throws Exception {
        UUID expense = expense("10.00");
        Tokens other = registerAndLogin("otro");

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/transactions/" + expense).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/transactions/" + expense).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        assertBalance(account, "990.00");
    }

    private UUID expense(String amount) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("accountId", account, "type", "EXPENSE", "amount", amount,
                "currencyCode", "EUR", "date", LocalDate.now().toString(), "categoryId", leisure,
                "description", "Concierto"));
        return id(post("/api/v1/transactions", body));
    }

    private ResultActions refund(UUID expense, Map<String, ?> body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/transactions/" + expense + "/refunds")
                .header("Authorization", user.bearer())
                .contentType("application/json").content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions delete(UUID transactionId) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/transactions/" + transactionId)
                .header("Authorization", user.bearer()));
    }

    private ResultActions get(String url) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", user.bearer()));
    }

    private JsonNode list(Map<String, String> params) throws Exception {
        var request = MockMvcRequestBuilders.get("/api/v1/transactions").header("Authorization", user.bearer());
        params.forEach(request::param);
        return objectMapper.readTree(mockMvc.perform(request).andReturn().getResponse().getContentAsString());
    }

    private void assertBalance(UUID accountId, String expected) throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accounts/" + accountId)
                .header("Authorization", user.bearer())).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).get("balance").decimalValue()).isEqualByComparingTo(expected);
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
