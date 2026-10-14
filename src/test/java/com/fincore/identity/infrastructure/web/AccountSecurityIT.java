package com.fincore.identity.infrastructure.web;

import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountSecurityIT extends IntegrationTest {

    private static final String NEW_PASSWORD = "otra-contraseña-nueva-larga";

    @Test
    void registrationSendsAVerificationLinkThatWorksOnlyOnce() throws Exception {
        Tokens tokens = registerAndLogin("Móvil");
        String token = mailer.lastVerificationToken(tokens.email()).orElseThrow();

        post("/api/v1/auth/verify-email", Map.of("token", token)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", tokens.bearer()))
                .andExpect(jsonPath("$.emailVerified").value(true));

        post("/api/v1/auth/verify-email", Map.of("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void forgotPasswordAnswersTheSameForUnknownEmails() throws Exception {
        String unknown = "nadie-" + UUID.randomUUID() + "@fincore.test";

        post("/api/v1/auth/forgot-password", Map.of("email", unknown)).andExpect(status().isAccepted());

        assertThat(mailer.lastResetToken(unknown)).isEmpty();
    }

    @Test
    void passwordResetChangesThePasswordAndClosesEverySession() throws Exception {
        Tokens tokens = registerAndLogin("Portátil");
        post("/api/v1/auth/forgot-password", Map.of("email", tokens.email())).andExpect(status().isAccepted());
        String resetToken = mailer.lastResetToken(tokens.email()).orElseThrow();

        post("/api/v1/auth/reset-password", Map.of("token", resetToken, "newPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", tokens.bearer()))
                .andExpect(status().isUnauthorized());
        loginAttempt(tokens.email(), TEST_PASSWORD).andExpect(status().isUnauthorized());
        loginAttempt(tokens.email(), NEW_PASSWORD).andExpect(status().isOk());
        post("/api/v1/auth/reset-password", Map.of("token", resetToken, "newPassword", "y-otra-mas-larga-aun"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void askingForANewResetLinkInvalidatesThePreviousOne() throws Exception {
        Tokens tokens = registerAndLogin("Portátil");
        post("/api/v1/auth/forgot-password", Map.of("email", tokens.email()));
        String first = mailer.lastResetToken(tokens.email()).orElseThrow();
        post("/api/v1/auth/forgot-password", Map.of("email", tokens.email()));
        String second = mailer.lastResetToken(tokens.email()).orElseThrow();

        post("/api/v1/auth/reset-password", Map.of("token", first, "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
        post("/api/v1/auth/reset-password", Map.of("token", second, "newPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());
    }

    @Test
    void passwordResetUnlocksALockedAccount() throws Exception {
        Tokens tokens = registerAndLogin("setup");
        for (int i = 0; i < 5; i++) {
            loginAttempt(tokens.email(), "contraseña-equivocada");
        }
        loginAttempt(tokens.email(), TEST_PASSWORD).andExpect(status().isTooManyRequests());

        post("/api/v1/auth/forgot-password", Map.of("email", tokens.email()));
        post("/api/v1/auth/reset-password", Map.of(
                "token", mailer.lastResetToken(tokens.email()).orElseThrow(), "newPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());

        loginAttempt(tokens.email(), NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void changePasswordKeepsTheCurrentSessionAndClosesTheOthers() throws Exception {
        Tokens current = registerAndLogin("Actual");
        Tokens other = login(current.email(), TEST_PASSWORD, "Otro");

        changePassword(current, "no-es-la-actual-para-nada", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"));
        changePassword(current, TEST_PASSWORD, TEST_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));

        changePassword(current, TEST_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", current.bearer())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", other.bearer())).andExpect(status().isUnauthorized());
        loginAttempt(current.email(), TEST_PASSWORD).andExpect(status().isUnauthorized());
        loginAttempt(current.email(), NEW_PASSWORD).andExpect(status().isOk());
    }

    private ResultActions changePassword(Tokens tokens, String currentPassword, String newPassword) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/auth/change-password")
                .header("Authorization", tokens.bearer())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                        Map.of("currentPassword", currentPassword, "newPassword", newPassword))));
    }

    private ResultActions loginAttempt(String email, String password) throws Exception {
        return post("/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    private ResultActions post(String url, Map<String, String> body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)));
    }
}
