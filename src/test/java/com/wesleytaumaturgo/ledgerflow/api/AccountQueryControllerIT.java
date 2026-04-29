package com.wesleytaumaturgo.ledgerflow.api;

import com.wesleytaumaturgo.ledgerflow.IntegrationTestBase;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for AccountQueryController.
 *
 * Tests cover the full stack from HTTP request through query use case to
 * the read model backed by real PostgreSQL (via Testcontainers).
 *
 * Flow: command side writes (create + deposit) → projector updates read model →
 * query endpoints return correct read model state.
 *
 * Since @EventListener is synchronous and shares the write transaction, the
 * projector fires within the same HTTP request. By the time the REST response
 * for a command is received, account_summary and transaction_history are already
 * consistent — no async wait needed.
 */
class AccountQueryControllerIT extends IntegrationTestBase {

    // ── GET /balance ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /balance returns 200 with correct balance after create and deposit")
    void getBalance_afterCreateAndDeposit_returns200WithCorrectBalance() {
        // Arrange: create account via command side
        ResponseEntity<CreateAccountResponse> createResp = restTemplate.postForEntity(
            "/api/v1/accounts",
            new CreateAccountRequest("owner-query-it"),
            CreateAccountResponse.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID accountId = createResp.getBody().accountId();

        // Arrange: deposit money
        ResponseEntity<DepositMoneyResponse> depositResp = restTemplate.postForEntity(
            "/api/v1/accounts/" + accountId + "/deposit",
            new DepositMoneyRequest(new BigDecimal("200.00"), "BRL"),
            DepositMoneyResponse.class);
        assertThat(depositResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Act: query balance
        ResponseEntity<BalanceResponseBody> balanceResp = restTemplate.getForEntity(
            "/api/v1/accounts/" + accountId + "/balance",
            BalanceResponseBody.class);

        // Assert
        assertThat(balanceResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        BalanceResponseBody body = balanceResp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.accountId()).isEqualTo(accountId);
        assertThat(body.balance()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(body.currency()).isEqualTo("BRL");
        assertThat(body.transactionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("GET /balance returns 404 ACCOUNT_NOT_FOUND for non-existent account")
    void getBalance_nonExistentAccount_returns404() {
        UUID nonExistentId = UUID.randomUUID();

        ResponseEntity<ErrorBody> resp = restTemplate.getForEntity(
            "/api/v1/accounts/" + nonExistentId + "/balance",
            ErrorBody.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().errorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    // ── GET /transactions ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /transactions returns 200 with correct pagination after deposit")
    void getTransactions_afterDeposit_returns200WithPaginationMetadata() {
        // Arrange: create account
        ResponseEntity<CreateAccountResponse> createResp = restTemplate.postForEntity(
            "/api/v1/accounts",
            new CreateAccountRequest("owner-tx-it"),
            CreateAccountResponse.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID accountId = createResp.getBody().accountId();

        // Arrange: deposit money
        ResponseEntity<DepositMoneyResponse> depositResp = restTemplate.postForEntity(
            "/api/v1/accounts/" + accountId + "/deposit",
            new DepositMoneyRequest(new BigDecimal("75.00"), "BRL"),
            DepositMoneyResponse.class);
        assertThat(depositResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Act: query transaction history
        ResponseEntity<TransactionsPageBody> txResp = restTemplate.getForEntity(
            "/api/v1/accounts/" + accountId + "/transactions",
            TransactionsPageBody.class);

        // Assert: pagination metadata
        assertThat(txResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransactionsPageBody body = txResp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.totalElements()).isEqualTo(1);
        assertThat(body.page()).isZero();
        assertThat(body.size()).isEqualTo(20);
        assertThat(body.totalPages()).isEqualTo(1);
        assertThat(body.content()).hasSize(1);
    }

    @Test
    @DisplayName("GET /transactions for non-existent account returns 404")
    void getTransactions_nonExistentAccount_returns404() {
        UUID nonExistentId = UUID.randomUUID();

        ResponseEntity<ErrorBody> resp = restTemplate.getForEntity(
            "/api/v1/accounts/" + nonExistentId + "/transactions",
            ErrorBody.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().errorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    // ── Response body helpers (package-private records for deserialization) ─────

    record BalanceResponseBody(
        UUID accountId,
        java.math.BigDecimal balance,
        String currency,
        int transactionCount,
        java.time.Instant lastTransactionAt
    ) {}

    record TransactionsPageBody(
        java.util.List<Object> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {}

    record ErrorBody(String errorCode, String detail) {}
}
