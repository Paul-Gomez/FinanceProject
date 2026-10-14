package com.fincore.goals.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoalIT extends IntegrationTest {

    private Tokens user;

    @BeforeEach
    void setUp() throws Exception {
        user = registerAndLogin("tests");
    }

    @Test
    void contributionsUpdateProgressAndAchieveTheGoal() throws Exception {
        UUID goalId = createGoal("1000.00", LocalDate.now().plusMonths(10));

        contribute(goalId, "400.00", "DEPOSIT")
                .andExpect(jsonPath("$.savedAmount").value(400.00))
                .andExpect(jsonPath("$.remainingAmount").value(600.00))
                .andExpect(jsonPath("$.progressPercentage").value(40.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.monthlyRecommended").exists());

        contribute(goalId, "600.00", "DEPOSIT")
                .andExpect(jsonPath("$.status").value("ACHIEVED"))
                .andExpect(jsonPath("$.monthlyRecommended").doesNotExist());

        mockMvc.perform(get("/api/v1/goals/" + goalId + "/contributions").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.content[0].type").value("GOAL_ACHIEVED"));
    }

    @Test
    void cannotWithdrawMoreThanSavedOrUseACancelledGoal() throws Exception {
        UUID goalId = createGoal("500.00", null);
        contribute(goalId, "100.00", "DEPOSIT");

        contribute(goalId, "150.00", "WITHDRAWAL")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_GOAL_OPERATION"));

        perform(post("/api/v1/goals/" + goalId + "/cancel"), Map.of()).andExpect(jsonPath("$.status").value("CANCELLED"));
        contribute(goalId, "10.00", "DEPOSIT").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectsTargetDateInThePastAndHidesOthersGoals() throws Exception {
        perform(post("/api/v1/goals"), Map.of("name", "Viaje", "targetAmount", "100.00", "currencyCode", "EUR",
                "targetDate", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isUnprocessableEntity());

        UUID goalId = createGoal("100.00", null);
        Tokens other = registerAndLogin("otro");
        mockMvc.perform(get("/api/v1/goals/" + goalId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentDepositsAreAllCounted() throws Exception {
        UUID goalId = createGoal("10000.00", null);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> contribute(goalId, "5.00", "DEPOSIT").andReturn().getResponse().getStatus());
        }
        pool.invokeAll(tasks);
        pool.shutdown();

        mockMvc.perform(get("/api/v1/goals/" + goalId).header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.savedAmount").value(100.00));
    }

    private UUID createGoal(String target, LocalDate targetDate) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("name", "Coche", "targetAmount", target,
                "currencyCode", "EUR"));
        if (targetDate != null) {
            body.put("targetDate", targetDate.toString());
        }
        JsonNode json = objectMapper.readTree(perform(post("/api/v1/goals"), body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return UUID.fromString(json.get("id").asText());
    }

    private ResultActions contribute(UUID goalId, String amount, String type) throws Exception {
        return perform(post("/api/v1/goals/" + goalId + "/contributions"), Map.of("amount", amount, "type", type));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Map<String, ?> body) throws Exception {
        return mockMvc.perform(request.header("Authorization", user.bearer())
                .contentType("application/json").content(objectMapper.writeValueAsString(body)));
    }
}
