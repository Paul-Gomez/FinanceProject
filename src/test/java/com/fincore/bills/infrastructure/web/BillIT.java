package com.fincore.bills.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.bills.application.BillService;
import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillIT extends IntegrationTest {

    @Autowired
    private BillService billService;

    private Tokens user;
    private UUID accountId;

    @BeforeEach
    void setUp() throws Exception {
        user = registerAndLogin("tests");
        accountId = id(perform(post("/api/v1/accounts"), Map.of("name", "Cuenta", "type", "BANK",
                "currencyCode", "EUR", "initialBalance", "500.00")));
    }

    @Test
    void payingMarksItPaidAndRecordsTheExpense() throws Exception {
        UUID billId = createBill("75.40", LocalDate.now().plusDays(10));

        JsonNode paid = objectMapper.readTree(pay(billId, accountId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andReturn().getResponse().getContentAsString());

        assertThat(paid.get("paidTransactionId").asText()).isNotBlank();
        mockMvc.perform(get("/api/v1/accounts/" + accountId).header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.balance").value(424.60));
    }

    @Test
    void forbiddenTransitionsAreRejected() throws Exception {
        UUID billId = createBill("20.00", LocalDate.now().plusDays(5));
        pay(billId, accountId).andExpect(status().isOk());

        pay(billId, accountId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_BILL_TRANSITION"));
        perform(post("/api/v1/bills/" + billId + "/cancel"), Map.of()).andExpect(status().isConflict());
        perform(put("/api/v1/bills/" + billId), billBody("25.00", LocalDate.now().plusDays(5)))
                .andExpect(status().isConflict());
    }

    @Test
    void failedPaymentLeavesTheBillPending() throws Exception {
        UUID billId = createBill("20.00", LocalDate.now().plusDays(5));
        Tokens other = registerAndLogin("otro");
        UUID othersAccount = UUID.fromString(objectMapper.readTree(mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", other.bearer()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("name", "Ajena", "type", "BANK", "currencyCode", "EUR"))))
                .andReturn().getResponse().getContentAsString()).get("id").asText());

        pay(billId, othersAccount).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/bills/" + billId).header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void concurrentPaymentsCreateOnlyOneExpense() throws Exception {
        UUID billId = createBill("10.00", LocalDate.now().plusDays(5));

        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> pay(billId, accountId).andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : pool.invokeAll(tasks)) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).containsOnlyOnce(200);
        mockMvc.perform(get("/api/v1/accounts/" + accountId).header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.balance").value(490.00));
    }

    @Test
    void billsBecomeOverdueAndTheJobIsIdempotent() throws Exception {
        UUID future = createBill("10.00", LocalDate.now().plusDays(1));
        UUID alreadyOverdue = createBill("10.00", LocalDate.now().minusDays(2));
        mockMvc.perform(get("/api/v1/bills/" + alreadyOverdue).header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.status").value("OVERDUE"));

        jdbcUpdate("UPDATE bills SET status = 'PENDING', due_date = current_date - 1 WHERE id = '" + future + "'");
        assertThat(billService.markOverdueBills()).extracting(b -> b.id()).contains(future);
        assertThat(billService.markOverdueBills()).extracting(b -> b.id()).doesNotContain(future);

        mockMvc.perform(get("/api/v1/bills").param("status", "OVERDUE").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.length()").value(2));
        pay(future, accountId).andExpect(status().isOk());
    }

    @Test
    void rejectsDueDateBeforeIssueDate() throws Exception {
        perform(post("/api/v1/bills"), Map.of("provider", "Luz", "amount", "50.00", "currencyCode", "EUR",
                "issueDate", LocalDate.now().toString(), "dueDate", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_BILL_DATES"));
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private void jdbcUpdate(String sql) {
        jdbc.update(sql);
    }

    private Map<String, Object> billBody(String amount, LocalDate dueDate) {
        return Map.of("provider", "Iberdrola", "amount", amount, "currencyCode", "EUR",
                "issueDate", dueDate.minusDays(15).toString(), "dueDate", dueDate.toString());
    }

    private UUID createBill(String amount, LocalDate dueDate) throws Exception {
        return id(perform(post("/api/v1/bills"), billBody(amount, dueDate)).andExpect(status().isCreated()));
    }

    private ResultActions pay(UUID billId, UUID account) throws Exception {
        return perform(post("/api/v1/bills/" + billId + "/pay"), Map.of("accountId", account));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Map<String, ?> body) throws Exception {
        return mockMvc.perform(request.header("Authorization", user.bearer())
                .contentType("application/json").content(objectMapper.writeValueAsString(body)));
    }

    private UUID id(ResultActions result) throws Exception {
        return UUID.fromString(objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText());
    }
}
