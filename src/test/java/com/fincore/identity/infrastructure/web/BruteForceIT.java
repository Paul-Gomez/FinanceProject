package com.fincore.identity.infrastructure.web;

import com.fincore.support.IntegrationTest;
import com.fincore.users.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BruteForceIT extends IntegrationTest {

    @Test
    void fiveFailedAttemptsLockTheAccountEvenForTheRightPassword() throws Exception {
        String email = registerAndLogin("setup").email();

        for (int i = 0; i < 5; i++) {
            attempt(email, "contraseña-equivocada").andExpect(status().isUnauthorized());
        }

        attempt(email, TEST_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void successfulLoginResetsTheCounter() throws Exception {
        String email = registerAndLogin("setup").email();

        for (int i = 0; i < 4; i++) {
            attempt(email, "contraseña-equivocada").andExpect(status().isUnauthorized());
        }
        attempt(email, TEST_PASSWORD).andExpect(status().isOk());

        for (int i = 0; i < 4; i++) {
            attempt(email, "contraseña-equivocada").andExpect(status().isUnauthorized());
        }
        attempt(email, TEST_PASSWORD).andExpect(status().isOk());
        assertThat(userRepository.findByEmail(email).map(AppUser::getFailedLoginAttempts)).contains(0);
    }

    @Test
    void concurrentFailuresAreAllCounted() throws Exception {
        String email = registerAndLogin("setup").email();

        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> attempt(email, "contraseña-equivocada").andReturn().getResponse().getStatus());
        }
        pool.invokeAll(tasks);
        pool.shutdown();

        attempt(email, TEST_PASSWORD).andExpect(status().isTooManyRequests());
    }

    private ResultActions attempt(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }
}
