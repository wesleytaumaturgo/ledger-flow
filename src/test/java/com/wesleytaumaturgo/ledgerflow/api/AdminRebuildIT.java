package com.wesleytaumaturgo.ledgerflow.api;

import com.wesleytaumaturgo.ledgerflow.IntegrationTestBase;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the admin rebuild endpoint.
 *
 * Tests the full rebuild flow against real PostgreSQL:
 *   1. Create account + deposit money via command side
 *   2. Projector fires synchronously (same transaction) — account_summary updated
 *   3. Manually corrupt account_summary via JdbcTemplate (simulates read model drift)
 *   4. POST /api/v1/admin/accounts/{id}/rebuild with correct X-Admin-Key
 *   5. Assert response has rebuiltEvents=2 (AccountCreated + MoneyDeposited)
 *   6. Assert account_summary restored to correct balance=100.00
 *   7. Assert transaction_history has 1 row (MoneyDeposited)
 *
 * Auth enforcement (401) is tested in AdminControllerTest (@WebMvcTest slice test)
 * which covers all 401 scenarios via MockMvc. The auth tests are not duplicated here
 * because JDK's HttpURLConnection handles 401 internally and throws HttpRetryException
 * in streaming mode — not suitable for RestTemplate-based IT tests.
 *
 * POST /api/v1/admin/accounts/{id}/rebuild — REQ-rebuild-read-model / FR-015
 */
class AdminRebuildIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${ADMIN_API_KEY}")
    private String adminApiKey;

    // ── Full rebuild flow ───────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /rebuild with correct key rebuilds from corrupted state")
    void rebuild_correctKey_rebuildsFromCorruptedState() {
        // Step 1: Create account
        ResponseEntity<CreateAccountResponse> createResp = restTemplate.postForEntity(
            "/api/v1/accounts",
            new CreateAccountRequest("owner-admin-rebuild"),
            CreateAccountResponse.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID accountId = createResp.getBody().accountId();

        // Step 2: Deposit money — projector fires synchronously within same transaction
        ResponseEntity<DepositMoneyResponse> depositResp = restTemplate.postForEntity(
            "/api/v1/accounts/" + accountId + "/deposit",
            new DepositMoneyRequest(new BigDecimal("100.00"), "BRL"),
            DepositMoneyResponse.class);
        assertThat(depositResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Step 3: Verify account_summary is correct before corruption
        BigDecimal balanceBeforeCorruption = jdbcTemplate.queryForObject(
            "SELECT current_balance FROM account_summary WHERE account_id = ?",
            BigDecimal.class, accountId);
        assertThat(balanceBeforeCorruption).isEqualByComparingTo("100.00");

        // Step 4: Corrupt account_summary — simulate read model drift
        jdbcTemplate.update(
            "UPDATE account_summary SET current_balance = 999.99 WHERE account_id = ?",
            accountId);
        BigDecimal corruptedBalance = jdbcTemplate.queryForObject(
            "SELECT current_balance FROM account_summary WHERE account_id = ?",
            BigDecimal.class, accountId);
        assertThat(corruptedBalance).isEqualByComparingTo("999.99");

        // Step 5: POST /rebuild with correct X-Admin-Key
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Key", adminApiKey);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<RebuildResponseBody> rebuildResp = restTemplate.exchange(
            "/api/v1/admin/accounts/" + accountId + "/rebuild",
            HttpMethod.POST,
            request,
            RebuildResponseBody.class);

        // Step 6: Assert response 200 with rebuiltEvents=2 (AccountCreated + MoneyDeposited)
        assertThat(rebuildResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        RebuildResponseBody body = rebuildResp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.accountId()).isEqualTo(accountId);
        assertThat(body.rebuiltEvents()).isEqualTo(2);

        // Step 7: Assert account_summary restored to correct balance=100.00
        BigDecimal restoredBalance = jdbcTemplate.queryForObject(
            "SELECT current_balance FROM account_summary WHERE account_id = ?",
            BigDecimal.class, accountId);
        assertThat(restoredBalance).isEqualByComparingTo("100.00");

        // Step 8: Assert transaction_history has exactly 1 row (MoneyDeposited)
        Integer historyCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transaction_history WHERE account_id = ?",
            Integer.class, accountId);
        assertThat(historyCount).isEqualTo(1);
    }

    // ── Response body helpers ───────────────────────────────────────────────────

    record RebuildResponseBody(UUID accountId, int rebuiltEvents) {}
}
