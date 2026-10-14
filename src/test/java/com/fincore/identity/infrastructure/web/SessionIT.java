package com.fincore.identity.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionIT extends IntegrationTest {

    @Test
    void refreshRotatesTheRefreshToken() throws Exception {
        Tokens tokens = registerAndLogin("Móvil");

        JsonNode refreshed = objectMapper.readTree(refresh(tokens.refreshToken())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String newRefresh = refreshed.get("refreshToken").asText();

        assertThat(newRefresh).isNotEqualTo(tokens.refreshToken());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + refreshed.get("accessToken").asText()))
                .andExpect(status().isOk());
        refresh(newRefresh).andExpect(status().isOk());
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeSession() throws Exception {
        Tokens tokens = registerAndLogin("Portátil");
        String rotated = objectMapper.readTree(refresh(tokens.refreshToken())
                .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();

        refresh(tokens.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        refresh(rotated).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", tokens.bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutCutsAccessImmediately() throws Exception {
        Tokens tokens = registerAndLogin("Navegador");

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", tokens.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", tokens.bearer()))
                .andExpect(status().isUnauthorized());
        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void listsDevicesAndRevokesOneFromAnother() throws Exception {
        Tokens phone = registerAndLogin("Móvil");
        Tokens laptop = login(phone.email(), TEST_PASSWORD, "Portátil");

        JsonNode sessions = objectMapper.readTree(mockMvc.perform(get("/api/v1/sessions")
                        .header("Authorization", laptop.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString());

        String phoneSessionId = null;
        for (JsonNode session : sessions) {
            if (!session.get("current").asBoolean()) {
                phoneSessionId = session.get("id").asText();
            }
        }
        mockMvc.perform(delete("/api/v1/sessions/" + phoneSessionId).header("Authorization", laptop.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", phone.bearer()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", laptop.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void revokeOthersKeepsOnlyTheCurrentSession() throws Exception {
        Tokens first = registerAndLogin("Uno");
        Tokens second = login(first.email(), TEST_PASSWORD, "Dos");
        Tokens third = login(first.email(), TEST_PASSWORD, "Tres");

        mockMvc.perform(delete("/api/v1/sessions").header("Authorization", third.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", first.bearer())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", second.bearer())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/sessions").header("Authorization", third.bearer()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void cannotRevokeSomeoneElsesSession() throws Exception {
        Tokens mine = registerAndLogin("Mío");
        Tokens theirs = registerAndLogin("Suyo");
        String theirSessionId = objectMapper.readTree(mockMvc.perform(get("/api/v1/sessions")
                .header("Authorization", theirs.bearer())).andReturn().getResponse().getContentAsString())
                .get(0).get("id").asText();

        mockMvc.perform(delete("/api/v1/sessions/" + theirSessionId).header("Authorization", mine.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", theirs.bearer())).andExpect(status().isOk());
    }

    @Test
    void unknownRefreshTokenIsRejected() throws Exception {
        refresh("token-" + UUID.randomUUID()).andExpect(status().isUnauthorized());
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }
}
