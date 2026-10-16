package com.fincore.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.support.IntegrationTest;
import com.fincore.users.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditIT extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void sensitiveActionsAreRecordedWithIpAndWithoutAmounts() throws Exception {
        Tokens user = registerAndLogin("Portátil");
        UUID account = id(post(user, "/api/v1/accounts", Map.of("name", "Banco", "type", "BANK", "currencyCode", "EUR",
                "initialBalance", "1234.56")));
        post(user, "/api/v1/transactions", Map.of("accountId", account, "type", "EXPENSE", "amount", "99.99",
                "currencyCode", "EUR"));

        JsonNode logs = myAudit(user);
        assertThat(actions(logs)).contains("REGISTER", "LOGIN", "CREATE_ACCOUNT", "CREATE_TRANSACTION");
        assertThat(logs.toString()).doesNotContain("1234.56").doesNotContain("99.99");
        assertThat(logs.get("content").get(0).get("ipAddress").asText()).isEqualTo("127.0.0.1");
    }

    @Test
    void failedLoginIsRecordedEvenThoughTheLoginRollsBack() throws Exception {
        Tokens user = registerAndLogin("setup");
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("email", user.email(), "password", "mala-mala-mala"))))
                .andExpect(status().isUnauthorized());

        UUID userId = userRepository.findByEmail(user.email()).map(AppUser::getId).orElseThrow();
        List<String> reasons = jdbc.queryForList(
                "SELECT details->>'reason' FROM audit_logs WHERE user_id = ? AND action = 'LOGIN_FAILED'", String.class, userId);
        assertThat(reasons).containsExactly("WRONG_PASSWORD");
    }

    @Test
    void operationsThatRollBackAreNotRecorded() throws Exception {
        Tokens user = registerAndLogin("tests");
        UUID account = id(post(user, "/api/v1/accounts", Map.of("name", "Banco", "type", "BANK", "currencyCode", "EUR")));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/transfers").header("Authorization", user.bearer())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("fromAccountId", account,
                                "toAccountId", UUID.randomUUID(), "amount", "10.00", "currencyCode", "EUR"))))
                .andExpect(status().isNotFound());

        assertThat(actions(myAudit(user))).doesNotContain("CREATE_TRANSFER");
    }

    @Test
    void usersSeeOnlyTheirOwnLogAndAdminsSeeEveryone() throws Exception {
        Tokens user = registerAndLogin("uno");
        Tokens other = registerAndLogin("dos");
        UUID otherId = userRepository.findByEmail(other.email()).map(AppUser::getId).orElseThrow();

        for (JsonNode log : myAudit(user).get("content")) {
            assertThat(log.get("userId").asText()).isNotEqualTo(otherId.toString());
        }
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/audit").header("Authorization", user.bearer()))
                .andExpect(status().isForbidden());

        AppUser admin = userRepository.findByEmail(user.email()).orElseThrow();
        admin.promoteToAdmin();
        userRepository.save(admin);
        Tokens adminSession = login(user.email(), TEST_PASSWORD, "admin");
        String body = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/audit").param("userId", otherId.toString())
                        .header("Authorization", adminSession.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(actions(objectMapper.readTree(body))).contains("REGISTER", "LOGIN");
    }

    private JsonNode myAudit(Tokens user) throws Exception {
        return objectMapper.readTree(mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/audit").param("size", "100")
                .header("Authorization", user.bearer())).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> actions(JsonNode page) {
        List<String> actions = new ArrayList<>();
        page.get("content").forEach(log -> actions.add(log.get("action").asText()));
        return actions;
    }

    private JsonNode post(Tokens user, String url, Map<String, ?> body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(MockMvcRequestBuilders.post(url).header("Authorization", user.bearer())
                        .contentType("application/json").content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }
}
