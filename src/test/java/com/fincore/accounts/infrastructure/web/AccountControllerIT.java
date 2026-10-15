package com.fincore.accounts.infrastructure.web;

import com.fincore.support.IntegrationTest;
import com.fincore.accounts.infrastructure.web.dto.ChangeAccountStatusRequest;
import com.fincore.accounts.infrastructure.web.dto.CreateAccountRequest;
import com.fincore.accounts.infrastructure.web.dto.UpdateAccountRequest;
import com.fincore.accounts.domain.AccountStatus;
import com.fincore.accounts.domain.AccountType;
import com.fincore.users.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountControllerIT extends IntegrationTest {

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
                        .header("Authorization", bearer(ownerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cuenta nómina"))
                .andExpect(jsonPath("$.balance").value(500.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/accounts").header("Authorization", bearer(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rejectsDuplicateOpenAccountNameButAllowsReusingAClosedOne() throws Exception {
        UUID first = createAccount(ownerId, "Ahorro");
        UUID other = createAccount(ownerId, "Otra");

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer(ownerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("Ahorro", AccountType.SAVINGS, "EUR", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NAME_ALREADY_EXISTS"));

        mockMvc.perform(put("/api/v1/accounts/" + other)
                        .header("Authorization", bearer(ownerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateAccountRequest("Ahorro"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NAME_ALREADY_EXISTS"));

        mockMvc.perform(patch("/api/v1/accounts/" + first + "/status")
                        .header("Authorization", bearer(ownerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChangeAccountStatusRequest(AccountStatus.CLOSED))))
                .andExpect(status().isOk());
        createAccount(ownerId, "Ahorro");
    }

    @Test
    void frameworkErrorsUseTheCommonErrorFormat() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/accounts"))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer(ownerId))
                        .contentType("application/json")
                        .content("{\"name\":\"X\",\"type\":\"NOPE\",\"currencyCode\":\"EUR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer(ownerId))
                        .contentType("application/json")
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(get("/api/v1/accounts/no-es-un-uuid").header("Authorization", bearer(ownerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(delete("/api/v1/accounts").header("Authorization", bearer(ownerId)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(get("/api/v1/exchange-rates").header("Authorization", bearer(ownerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void rejectsClosingAnAccountWithBalance() throws Exception {
        String body = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer(ownerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("Con saldo", AccountType.BANK, "EUR", new BigDecimal("15.00")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID accountId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", bearer(ownerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChangeAccountStatusRequest(AccountStatus.CLOSED))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BALANCE_NOT_ZERO"));

        mockMvc.perform(get("/api/v1/accounts/" + accountId).header("Authorization", bearer(ownerId)))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void rejectsUnknownCurrencyCode() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest("Cuenta", AccountType.CASH, "ZZZ", null);

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer(ownerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));
    }

    @Test
    void deniesAccessToSomeoneElsesAccount() throws Exception {
        UUID accountId = createAccount(ownerId, "Cuenta privada");

        mockMvc.perform(get("/api/v1/accounts/" + accountId).header("Authorization", bearer(otherOwnerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(put("/api/v1/accounts/" + accountId)
                        .header("Authorization", bearer(otherOwnerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateAccountRequest("Hackeada"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidStatusTransition() throws Exception {
        UUID accountId = createAccount(ownerId, "Cuenta a cerrar");

        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", bearer(ownerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChangeAccountStatusRequest(AccountStatus.CLOSED))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", bearer(ownerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChangeAccountStatusRequest(AccountStatus.ACTIVE))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void returnsNotFoundForUnknownAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + UUID.randomUUID()).header("Authorization", bearer(ownerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    private UUID createAccount(UUID owner, String name) throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(name, AccountType.BANK, "EUR", null);

        String response = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer(owner))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
