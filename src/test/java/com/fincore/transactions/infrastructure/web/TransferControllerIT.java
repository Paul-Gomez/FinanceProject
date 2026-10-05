package com.fincore.transactions.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.accounts.domain.AccountStatus;
import com.fincore.accounts.domain.AccountType;
import com.fincore.accounts.infrastructure.web.dto.ChangeAccountStatusRequest;
import com.fincore.accounts.infrastructure.web.dto.CreateAccountRequest;
import com.fincore.transactions.infrastructure.web.dto.CreateTransferRequest;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransferControllerIT {

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
    void movesMoneyBetweenAccountsAndCreatesBothLegs() throws Exception {
        UUID from = createAccount(ownerId, "EUR", "500.00");
        UUID to = createAccount(ownerId, "EUR", "50.00");

        transfer(ownerId, from, to, "120.00", "EUR")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outgoing.amount").value(-120.00))
                .andExpect(jsonPath("$.incoming.amount").value(120.00))
                .andExpect(jsonPath("$.outgoing.type").value("TRANSFER"));

        assertBalance(ownerId, from, 380.00);
        assertBalance(ownerId, to, 170.00);
        assertMovementCount(ownerId, from, 1);
        assertMovementCount(ownerId, to, 1);
    }

    @Test
    void failedTransferLeavesBothAccountsUntouched() throws Exception {
        UUID from = createAccount(ownerId, "EUR", "500.00");
        UUID archivedDestination = createAccount(ownerId, "EUR", "50.00");
        mockMvc.perform(patch("/api/v1/accounts/" + archivedDestination + "/status")
                .header("X-User-Id", ownerId.toString())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new ChangeAccountStatusRequest(AccountStatus.ARCHIVED))));

        transfer(ownerId, from, archivedDestination, "100.00", "EUR")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));

        assertBalance(ownerId, from, 500.00);
        assertBalance(ownerId, archivedDestination, 50.00);
        assertMovementCount(ownerId, from, 0);
    }

    @Test
    void rejectsTransferBetweenDifferentCurrenciesWithoutMovingMoney() throws Exception {
        UUID eurAccount = createAccount(ownerId, "EUR", "500.00");
        UUID usdAccount = createAccount(ownerId, "USD", "50.00");

        transfer(ownerId, eurAccount, usdAccount, "100.00", "EUR")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));

        assertBalance(ownerId, eurAccount, 500.00);
        assertBalance(ownerId, usdAccount, 50.00);
    }

    @Test
    void rejectsTransferToSameAccount() throws Exception {
        UUID account = createAccount(ownerId, "EUR", "500.00");

        transfer(ownerId, account, account, "10.00", "EUR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT_TRANSFER"));

        assertMovementCount(ownerId, account, 0);
    }

    @Test
    void rejectsTransferToSomeoneElsesAccount() throws Exception {
        UUID mine = createAccount(ownerId, "EUR", "500.00");
        UUID theirs = createAccount(otherOwnerId, "EUR", "0.00");

        transfer(ownerId, mine, theirs, "100.00", "EUR").andExpect(status().isForbidden());

        assertBalance(ownerId, mine, 500.00);
        assertBalance(otherOwnerId, theirs, 0.00);
    }

    @Test
    void crossedConcurrentTransfersDoNotDeadlockAndKeepTotals() throws Exception {
        UUID a = createAccount(ownerId, "EUR", "1000.00");
        UUID b = createAccount(ownerId, "EUR", "1000.00");
        int perDirection = 10;

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < perDirection; i++) {
            tasks.add(() -> transfer(ownerId, a, b, "10.00", "EUR").andReturn().getResponse().getStatus());
            tasks.add(() -> transfer(ownerId, b, a, "10.00", "EUR").andReturn().getResponse().getStatus());
        }
        List<Future<Integer>> results = pool.invokeAll(tasks);
        pool.shutdown();

        for (Future<Integer> result : results) {
            assertThat(result.get()).isEqualTo(201);
        }
        assertBalance(ownerId, a, 1000.00);
        assertBalance(ownerId, b, 1000.00);
        assertMovementCount(ownerId, a, perDirection * 2);
    }

    private ResultActions transfer(UUID requester, UUID from, UUID to, String amount, String currency) throws Exception {
        CreateTransferRequest request = new CreateTransferRequest(
                from, to, new BigDecimal(amount), currency, "test", null, null);
        return mockMvc.perform(post("/api/v1/transfers")
                .header("X-User-Id", requester.toString())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)));
    }

    private void assertBalance(UUID owner, UUID accountId, double expected) throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + accountId).header("X-User-Id", owner.toString()))
                .andExpect(jsonPath("$.balance").value(expected));
    }

    private void assertMovementCount(UUID owner, UUID accountId, int expected) throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("accountId", accountId.toString())
                        .header("X-User-Id", owner.toString()))
                .andExpect(jsonPath("$.totalElements").value(expected));
    }

    private UUID createAccount(UUID owner, String currency, String initialBalance) throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(
                "Cuenta " + UUID.randomUUID(), AccountType.BANK, currency, new BigDecimal(initialBalance));
        String response = mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", owner.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
