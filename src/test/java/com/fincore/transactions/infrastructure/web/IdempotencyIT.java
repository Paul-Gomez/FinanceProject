package com.fincore.transactions.infrastructure.web;

import com.fincore.support.IntegrationTest;
import com.fincore.accounts.domain.AccountType;
import com.fincore.accounts.infrastructure.web.dto.CreateAccountRequest;
import com.fincore.transactions.domain.TransactionType;
import com.fincore.transactions.infrastructure.web.dto.CreateTransactionRequest;
import com.fincore.transactions.infrastructure.web.dto.CreateTransferRequest;
import com.fincore.users.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyIT extends IntegrationTest {

    private UUID ownerId;
    private UUID otherOwnerId;

    @BeforeEach
    void setUp() {
        ownerId = userRepository.save(new AppUser("owner-" + UUID.randomUUID() + "@fincore.test")).getId();
        otherOwnerId = userRepository.save(new AppUser("other-" + UUID.randomUUID() + "@fincore.test")).getId();
    }

    @Test
    void sameKeyAndSamePayloadCreatesOnlyOneMovementAndReplaysTheResponse() throws Exception {
        UUID account = createAccount(ownerId, "100.00");
        String key = UUID.randomUUID().toString();

        MockHttpServletResponse first = postExpense(ownerId, account, "30.00", key).andReturn().getResponse();
        MockHttpServletResponse second = postExpense(ownerId, account, "30.00", key).andReturn().getResponse();

        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(first.getHeader("Idempotent-Replayed")).isNull();
        assertThat(second.getStatus()).isEqualTo(201);
        assertThat(second.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(idOf(second)).isEqualTo(idOf(first));
        assertBalance(ownerId, account, 70.00);
        assertMovementCount(ownerId, account, 2);
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() throws Exception {
        UUID account = createAccount(ownerId, "100.00");
        String key = UUID.randomUUID().toString();

        postExpense(ownerId, account, "30.00", key).andExpect(status().isCreated());
        postExpense(ownerId, account, "45.00", key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertBalance(ownerId, account, 70.00);
        assertMovementCount(ownerId, account, 2);
    }

    @Test
    void concurrentRequestsWithTheSameKeyExecuteOnlyOnce() throws Exception {
        UUID account = createAccount(ownerId, "1000.00");
        String key = UUID.randomUUID().toString();
        int requests = 10;

        ExecutorService pool = Executors.newFixedThreadPool(requests);
        List<Callable<MockHttpServletResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            tasks.add(() -> postExpense(ownerId, account, "10.00", key).andReturn().getResponse());
        }
        List<Future<MockHttpServletResponse>> results = pool.invokeAll(tasks);
        pool.shutdown();

        Set<String> ids = new HashSet<>();
        for (Future<MockHttpServletResponse> result : results) {
            assertThat(result.get().getStatus()).isEqualTo(201);
            ids.add(idOf(result.get()));
        }
        assertThat(ids).hasSize(1);
        assertBalance(ownerId, account, 990.00);
        assertMovementCount(ownerId, account, 2);
    }

    @Test
    void failedRequestDoesNotConsumeTheKey() throws Exception {
        UUID othersAccount = createAccount(otherOwnerId, "100.00");
        UUID myAccount = createAccount(ownerId, "100.00");
        String key = UUID.randomUUID().toString();

        postExpense(ownerId, othersAccount, "10.00", key).andExpect(status().isForbidden());
        postExpense(ownerId, myAccount, "10.00", key).andExpect(status().isCreated());

        assertBalance(ownerId, myAccount, 90.00);
        assertBalance(otherOwnerId, othersAccount, 100.00);
    }

    @Test
    void sameKeyFromDifferentUsersIsIndependent() throws Exception {
        UUID mine = createAccount(ownerId, "100.00");
        UUID theirs = createAccount(otherOwnerId, "100.00");
        String key = UUID.randomUUID().toString();

        postExpense(ownerId, mine, "10.00", key).andExpect(status().isCreated());
        MockHttpServletResponse other = postExpense(otherOwnerId, theirs, "10.00", key).andReturn().getResponse();

        assertThat(other.getStatus()).isEqualTo(201);
        assertThat(other.getHeader("Idempotent-Replayed")).isNull();
        assertBalance(otherOwnerId, theirs, 90.00);
    }

    @Test
    void repeatedTransferWithSameKeyMovesMoneyOnlyOnce() throws Exception {
        UUID from = createAccount(ownerId, "500.00");
        UUID to = createAccount(ownerId, "0.00");
        String key = UUID.randomUUID().toString();

        MockHttpServletResponse first = postTransfer(from, to, "100.00", key).andReturn().getResponse();
        MockHttpServletResponse second = postTransfer(from, to, "100.00", key).andReturn().getResponse();

        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(second.getStatus()).isEqualTo(201);
        assertThat(second.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(objectMapper.readTree(second.getContentAsString()).get("transferId").asText())
                .isEqualTo(objectMapper.readTree(first.getContentAsString()).get("transferId").asText());
        assertBalance(ownerId, from, 400.00);
        assertBalance(ownerId, to, 100.00);
        assertMovementCount(ownerId, from, 2);
    }

    @Test
    void concurrentTransfersWithTheSameKeyExecuteOnlyOnce() throws Exception {
        UUID from = createAccount(ownerId, "500.00");
        UUID to = createAccount(ownerId, "0.00");
        String key = UUID.randomUUID().toString();
        int requests = 8;

        ExecutorService pool = Executors.newFixedThreadPool(requests);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            tasks.add(() -> postTransfer(from, to, "50.00", key).andReturn().getResponse().getStatus());
        }
        List<Future<Integer>> results = pool.invokeAll(tasks);
        pool.shutdown();

        for (Future<Integer> result : results) {
            assertThat(result.get()).isEqualTo(201);
        }
        assertBalance(ownerId, from, 450.00);
        assertBalance(ownerId, to, 50.00);
    }

    @Test
    void rejectsTooLongKey() throws Exception {
        UUID account = createAccount(ownerId, "100.00");

        postExpense(ownerId, account, "10.00", "k".repeat(101))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));

        assertMovementCount(ownerId, account, 1);
    }

    private org.springframework.test.web.servlet.ResultActions postExpense(
            UUID requester, UUID accountId, String amount, String key) throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                accountId, TransactionType.EXPENSE, new BigDecimal(amount), "EUR", null, "test", null, null, null);
        return mockMvc.perform(post("/api/v1/transactions")
                .header("X-User-Id", requester.toString())
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)));
    }

    private org.springframework.test.web.servlet.ResultActions postTransfer(
            UUID from, UUID to, String amount, String key) throws Exception {
        CreateTransferRequest request = new CreateTransferRequest(
                from, to, new BigDecimal(amount), "EUR", "test", null, null);
        return mockMvc.perform(post("/api/v1/transfers")
                .header("X-User-Id", ownerId.toString())
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)));
    }

    private String idOf(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString()).get("id").asText();
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
