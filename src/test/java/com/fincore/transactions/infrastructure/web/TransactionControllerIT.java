package com.fincore.transactions.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.accounts.domain.AccountType;
import com.fincore.accounts.infrastructure.web.dto.ChangeAccountStatusRequest;
import com.fincore.accounts.infrastructure.web.dto.CreateAccountRequest;
import com.fincore.accounts.domain.AccountStatus;
import com.fincore.transactions.domain.TransactionType;
import com.fincore.transactions.infrastructure.web.dto.CreateTransactionRequest;
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
class TransactionControllerIT {

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
    void incomeAndExpenseUpdateAccountBalance() throws Exception {
        UUID accountId = createAccount(ownerId, "1000.00");

        postTransaction(ownerId, accountId, TransactionType.INCOME, "250.50", "EUR").andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(250.50));
        postTransaction(ownerId, accountId, TransactionType.EXPENSE, "100.25", "EUR").andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(-100.25));

        mockMvc.perform(get("/api/v1/accounts/" + accountId).header("X-User-Id", ownerId.toString()))
                .andExpect(jsonPath("$.balance").value(1150.25));
    }

    @Test
    void rejectsMovementInDifferentCurrencyWithoutChangingBalance() throws Exception {
        UUID accountId = createAccount(ownerId, "100.00");

        postTransaction(ownerId, accountId, TransactionType.EXPENSE, "10.00", "USD")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));

        mockMvc.perform(get("/api/v1/accounts/" + accountId).header("X-User-Id", ownerId.toString()))
                .andExpect(jsonPath("$.balance").value(100.00));
        mockMvc.perform(get("/api/v1/transactions").param("accountId", accountId.toString())
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void deniesMovementOnSomeoneElsesAccount() throws Exception {
        UUID accountId = createAccount(ownerId, "100.00");

        postTransaction(otherOwnerId, accountId, TransactionType.EXPENSE, "10.00", "EUR")
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMovementOnArchivedAccount() throws Exception {
        UUID accountId = createAccount(ownerId, "100.00");
        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/status")
                .header("X-User-Id", ownerId.toString())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new ChangeAccountStatusRequest(AccountStatus.ARCHIVED))));

        postTransaction(ownerId, accountId, TransactionType.INCOME, "10.00", "EUR")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    void rejectsTransferTypeOnThisEndpoint() throws Exception {
        UUID accountId = createAccount(ownerId, "100.00");

        postTransaction(ownerId, accountId, TransactionType.TRANSFER, "10.00", "EUR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_TRANSACTION_TYPE"));
    }

    @Test
    void listsTransactionsPaginatedNewestFirst() throws Exception {
        UUID accountId = createAccount(ownerId, "0.00");
        for (int i = 1; i <= 5; i++) {
            postTransaction(ownerId, accountId, TransactionType.INCOME, i + ".00", "EUR").andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/v1/transactions").param("accountId", accountId.toString())
                        .param("page", "0").param("size", "2")
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void concurrentExpensesNeverLoseUpdates() throws Exception {
        UUID accountId = createAccount(ownerId, "1000.00");
        int requests = 20;

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            tasks.add(() -> postTransaction(ownerId, accountId, TransactionType.EXPENSE, "10.00", "EUR")
                    .andReturn().getResponse().getStatus());
        }
        List<Future<Integer>> results = pool.invokeAll(tasks);
        pool.shutdown();

        for (Future<Integer> result : results) {
            assertThat(result.get()).isEqualTo(201);
        }
        mockMvc.perform(get("/api/v1/accounts/" + accountId).header("X-User-Id", ownerId.toString()))
                .andExpect(jsonPath("$.balance").value(800.00));
    }

    private org.springframework.test.web.servlet.ResultActions postTransaction(
            UUID requester, UUID accountId, TransactionType type, String amount, String currency) throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                accountId, type, new BigDecimal(amount), currency, null, "test", null, null, null);
        return mockMvc.perform(post("/api/v1/transactions")
                .header("X-User-Id", requester.toString())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)));
    }

    private UUID createAccount(UUID owner, String initialBalance) throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(
                "Cuenta " + UUID.randomUUID(), AccountType.BANK, "EUR", new BigDecimal(initialBalance));
        String response = mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", owner.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
