package com.fincore.accounts.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.accounts.infrastructure.web.dto.ChangeAccountStatusRequest;
import com.fincore.accounts.infrastructure.web.dto.CreateAccountRequest;
import com.fincore.accounts.infrastructure.web.dto.UpdateAccountRequest;
import com.fincore.accounts.domain.AccountStatus;
import com.fincore.accounts.domain.AccountType;
import com.fincore.users.domain.AppUser;
import com.fincore.users.infrastructure.persistence.AppUserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AccountControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserJpaRepository userRepository;

    private UUID ownerId;
    private UUID otherOwnerId;

    @BeforeEach
    void setUp() {
        ownerId = userRepository.save(new AppUser("owner-" + UUID.randomUUID() + "@fincore.test")).getId();
        otherOwnerId = userRepository.save(new AppUser("other-" + UUID.randomUUID() + "@fincore.test")).getId();
    }

    @Test
    void createsAndListsAccountForOwner() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest("Cuenta nómina", AccountType.BANK, "EUR", new BigDecimal("500.00"));

        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", ownerId.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cuenta nómina"))
                .andExpect(jsonPath("$.balance").value(500.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/accounts").header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rejectsCreationForUnknownOwner() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest("Cuenta", AccountType.CASH, "EUR", null);

        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_OWNER"));
    }

    @Test
    void rejectsUnknownCurrencyCode() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest("Cuenta", AccountType.CASH, "ZZZ", null);

        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", ownerId.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));
    }

    @Test
    void deniesAccessToSomeoneElsesAccount() throws Exception {
        UUID accountId = createAccount(ownerId, "Cuenta privada");

        mockMvc.perform(get("/api/v1/accounts/" + accountId).header("X-User-Id", otherOwnerId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(put("/api/v1/accounts/" + accountId)
                        .header("X-User-Id", otherOwnerId.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateAccountRequest("Hackeada"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidStatusTransition() throws Exception {
        UUID accountId = createAccount(ownerId, "Cuenta a cerrar");

        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/status")
                        .header("X-User-Id", ownerId.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChangeAccountStatusRequest(AccountStatus.CLOSED))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/status")
                        .header("X-User-Id", ownerId.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChangeAccountStatusRequest(AccountStatus.ACTIVE))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void returnsNotFoundForUnknownAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + UUID.randomUUID()).header("X-User-Id", ownerId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    private UUID createAccount(UUID owner, String name) throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(name, AccountType.BANK, "EUR", null);

        String response = mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", owner.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
