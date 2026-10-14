package com.fincore.jobs;

import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScheduledJobsIT extends IntegrationTest {

    @Autowired
    private ScheduledJobs jobs;

    @Autowired
    private JdbcTemplate jdbc;

    private Tokens user;

    @BeforeEach
    void setUp() throws Exception {
        user = registerAndLogin("tests");
    }

    @Test
    void overdueBillsAreMarkedAndNotifiedOnlyOnce() throws Exception {
        String billId = createBill(LocalDate.now().plusDays(10));
        jdbc.update("UPDATE bills SET due_date = current_date - 1, issue_date = current_date - 20 WHERE id = ?::uuid", billId);

        jobs.runOverdueBills();
        jobs.runOverdueBills();

        mockMvc.perform(get("/api/v1/bills/" + billId).header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.status").value("OVERDUE"));
        assertThat(notificationsOfType("BILL_OVERDUE")).isEqualTo(1);
    }

    @Test
    void remindersForBillsAndSubscriptionsAreSentOnce() throws Exception {
        createBill(LocalDate.now().plusDays(2));
        perform(post("/api/v1/subscriptions"), Map.of("name", "Netflix", "amount", "12.99", "currencyCode", "EUR",
                "billingCycle", "MONTHLY", "startDate", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isCreated());

        jobs.runBillReminders();
        jobs.runSubscriptionReminders();
        jobs.runBillReminders();
        jobs.runSubscriptionReminders();

        assertThat(notificationsOfType("BILL_DUE_SOON")).isEqualTo(1);
        assertThat(notificationsOfType("SUBSCRIPTION_UPCOMING")).isEqualTo(1);
    }

    @Test
    void cleanupRemovesExpiredIdempotencyKeys() throws Exception {
        UUID userId = userRepository.findByEmail(user.email()).orElseThrow().getId();
        jdbc.update("""
                INSERT INTO idempotency_keys (user_id, idempotency_key, request_hash, created_at, expires_at)
                VALUES (?, 'vieja', 'hash', now() - interval '2 days', now() - interval '1 day')
                """, userId);

        jobs.cleanup();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE user_id = ?", Integer.class, userId))
                .isZero();
    }

    private int notificationsOfType(String type) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM notifications n JOIN users u ON u.id = n.user_id
                WHERE u.email = ? AND n.type = ?
                """, Integer.class, user.email(), type);
    }

    private String createBill(LocalDate dueDate) throws Exception {
        String body = perform(post("/api/v1/bills"), Map.of("provider", "Iberdrola", "amount", "60.00",
                "currencyCode", "EUR", "issueDate", LocalDate.now().toString(), "dueDate", dueDate.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions perform(MockHttpServletRequestBuilder request,
                                                                        Map<String, ?> body) throws Exception {
        return mockMvc.perform(request.header("Authorization", user.bearer())
                .contentType("application/json").content(objectMapper.writeValueAsString(body)));
    }
}
