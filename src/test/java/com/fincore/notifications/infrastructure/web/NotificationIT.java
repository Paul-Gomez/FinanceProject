package com.fincore.notifications.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.notifications.application.NotificationService;
import com.fincore.notifications.domain.NotificationType;
import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationIT extends IntegrationTest {

    @Autowired
    private NotificationService notificationService;

    private Tokens user;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        user = registerAndLogin("tests");
        userId = userRepository.findByEmail(user.email()).orElseThrow().getId();
    }

    @Test
    void budgetThresholdCreatesANotificationOnlyOnce() throws Exception {
        UUID account = id(post("/api/v1/accounts", Map.of("name", "Cuenta", "type", "BANK", "currencyCode", "EUR",
                "initialBalance", "1000.00")));
        UUID leisure = systemCategory("Ocio");
        post("/api/v1/budgets", Map.of("categoryId", leisure, "amount", "100.00", "currencyCode", "EUR", "period", "MONTHLY"));

        for (int i = 0; i < 3; i++) {
            post("/api/v1/transactions", Map.of("accountId", account, "type", "EXPENSE", "amount", "30.00",
                    "currencyCode", "EUR", "categoryId", leisure));
        }

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("BUDGET_THRESHOLD"))
                .andExpect(jsonPath("$.content[0].title").value("Has usado el 80 % del presupuesto de Ocio"));
    }

    @Test
    void sameDedupeKeyNotifiesOnlyOnce() {
        assertThat(notificationService.notify(userId, NotificationType.BILL_DUE_SOON, "t", "m", "bill:1:due")).isTrue();
        assertThat(notificationService.notify(userId, NotificationType.BILL_DUE_SOON, "t", "m", "bill:1:due")).isFalse();
        assertThat(notificationService.countUnread(userId)).isEqualTo(1);
    }

    @Test
    void readAndUnreadManagement() throws Exception {
        notificationService.notify(userId, NotificationType.GOAL_ACHIEVED, "Uno", "m", null);
        notificationService.notify(userId, NotificationType.GOAL_ACHIEVED, "Dos", "m", null);
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.unread").value(2));

        String firstId = objectMapper.readTree(mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", user.bearer())).andReturn().getResponse().getContentAsString())
                .get("content").get(0).get("id").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/notifications/" + firstId + "/read").header("Authorization", user.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/notifications").param("unreadOnly", "true").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/notifications/read-all").header("Authorization", user.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.unread").value(0));

        Tokens other = registerAndLogin("otro");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/notifications/" + firstId + "/read").header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
    }

    private JsonNode post(String url, Map<String, ?> body) throws Exception {
        String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                        .header("Authorization", user.bearer())
                        .contentType("application/json").content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
        return response.isEmpty() ? null : objectMapper.readTree(response);
    }

    private UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
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
