package com.fincore.identity.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.identity.infrastructure.web.dto.LoginRequest;
import com.fincore.identity.infrastructure.web.dto.RegisterRequest;
import com.fincore.support.IntegrationTest;
import com.fincore.users.domain.AppUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends IntegrationTest {

    private static final String PASSWORD = "una-contraseña-larga";

    @Test
    void registersUserWithNormalizedEmailAndHashedPassword() throws Exception {
        String email = "Nuevo." + UUID.randomUUID() + "@Example.COM";

        register(email, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        AppUser saved = userRepository.findByEmail(email.toLowerCase()).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2a$").doesNotContain(PASSWORD);
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@fincore.test";
        register(email, PASSWORD).andExpect(status().isCreated());

        register(email.toUpperCase(), PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void rejectsWeakPasswordAndInvalidEmail() throws Exception {
        register("corta-" + UUID.randomUUID() + "@fincore.test", "corta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
        register("no-es-un-email", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        // 25 caracteres de 3 bytes = 75 bytes: bcrypt ignoraría lo que pase de 72.
        register("larga-" + UUID.randomUUID() + "@fincore.test", "€".repeat(25))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
    }

    @Test
    void loginReturnsTokensThatGiveAccessToTheApi() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@fincore.test";
        register(email, PASSWORD).andExpect(status().isCreated());

        JsonNode tokens = objectMapper.readTree(login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString());

        assertThat(tokens.get("refreshToken").asText()).isNotBlank();
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        String email = "same-" + UUID.randomUUID() + "@fincore.test";
        register(email, PASSWORD).andExpect(status().isCreated());

        String wrongPassword = login(email, "otra-contraseña-mala").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String unknownEmail = login("nadie-" + UUID.randomUUID() + "@fincore.test", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(objectMapper.readTree(wrongPassword).get("message"))
                .isEqualTo(objectMapper.readTree(unknownEmail).get("message"));
        assertThat(objectMapper.readTree(wrongPassword).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void userWithoutPasswordCannotLogIn() throws Exception {
        String email = "sinpass-" + UUID.randomUUID() + "@fincore.test";
        userRepository.save(new AppUser(email));

        login(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRejectMissingOrForgedTokens() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized());

        String forged = Jwts.builder()
                .issuer("fincore")
                .subject(UUID.randomUUID().toString())
                .claim("sid", UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("otra-clave-que-no-es-la-del-servidor-1234567890".getBytes(StandardCharsets.UTF_8)))
                .compact();
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, password, "Test"))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password, "Portátil"))));
    }
}
