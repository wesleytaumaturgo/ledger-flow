package com.wesleytaumaturgo.ledgerflow.query.application.projectors;

import com.wesleytaumaturgo.ledgerflow.IntegrationTestBase;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.WithdrawMoneyRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.WithdrawMoneyResponse;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.EventStoreRepository;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.BalanceView;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.AccountSummaryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AccountProjector — verifies read model updates after command side writes.
 *
 * Since @EventListener is synchronous and shares the write transaction, the projector fires
 * within the same HTTP request. By the time the REST response is returned, account_summary
 * and transaction_history have already been updated.
 *
 * Uses TestRestTemplate to exercise the full stack via HTTP.
 * Assertions run against AccountSummaryRepository (domain port, backed by PostgreSQL).
 */
class AccountProjectorIT extends IntegrationTestBase {

    @Autowired
    private AccountSummaryRepository accountSummaryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EventStoreRepository eventStoreRepository;

    @Autowired
    private AccountProjector accountProjector;

    // ── Deposit projects correctly ──────────────────────────────────────────────

    @Test
    @DisplayName("deposit via use case: projector updates account_summary with balance and count")
    void deposit_via_useCase_projectsAccountSummary() {
        // Create account
        ResponseEntity<CreateAccountResponse> createResp = restTemplate.postForEntity(
            "/api/v1/accounts",
            new CreateAccountRequest("owner-it-projector"),
            CreateAccountResponse.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID accountId = createResp.getBody().accountId();

        // account_summary row should exist with balance=0 after AccountCreated event
        Optional<BalanceView> afterCreate = accountSummaryRepository.findById(accountId);
        assertThat(afterCreate).isPresent();
        assertThat(afterCreate.get().balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(afterCreate.get().transactionCount()).isZero();
        assertThat(afterCreate.get().lastEventSequence()).isEqualTo(1L);

        // Deposit money
        ResponseEntity<DepositMoneyResponse> depositResp = restTemplate.postForEntity(
            "/api/v1/accounts/" + accountId + "/deposit",
            new DepositMoneyRequest(new BigDecimal("250.00"), "BRL"),
            DepositMoneyResponse.class);
        assertThat(depositResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify account_summary updated
        Optional<BalanceView> afterDeposit = accountSummaryRepository.findById(accountId);
        assertThat(afterDeposit).isPresent();
        BalanceView view = afterDeposit.get();
        assertThat(view.balance()).isEqualByComparingTo("250.00");
        assertThat(view.totalDeposited()).isEqualByComparingTo("250.00");
        assertThat(view.totalWithdrawn()).isEqualByComparingTo("0.00");
        assertThat(view.transactionCount()).isEqualTo(1);
        assertThat(view.lastEventSequence()).isEqualTo(2L);
        assertThat(view.currency()).isEqualTo("BRL");

        // Verify transaction_history has 1 row for the deposit
        int historyCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transaction_history WHERE account_id = ?",
            Integer.class, accountId);
        assertThat(historyCount).isEqualTo(1);

        // Verify the transaction_history row content
        String eventType = jdbcTemplate.queryForObject(
            "SELECT event_type FROM transaction_history WHERE account_id = ?",
            String.class, accountId);
        assertThat(eventType).isEqualTo("MoneyDeposited");
    }

    // ── Withdraw projects correctly ─────────────────────────────────────────────

    @Test
    @DisplayName("withdraw via use case: projector decrements balance and totalWithdrawn")
    void withdraw_via_useCase_projectsAccountSummary() {
        // Create account
        ResponseEntity<CreateAccountResponse> createResp = restTemplate.postForEntity(
            "/api/v1/accounts",
            new CreateAccountRequest("owner-it-withdraw"),
            CreateAccountResponse.class);
        UUID accountId = createResp.getBody().accountId();

        // Deposit first
        restTemplate.postForEntity(
            "/api/v1/accounts/" + accountId + "/deposit",
            new DepositMoneyRequest(new BigDecimal("500.00"), "BRL"),
            DepositMoneyResponse.class);

        // Withdraw
        ResponseEntity<WithdrawMoneyResponse> withdrawResp = restTemplate.postForEntity(
            "/api/v1/accounts/" + accountId + "/withdraw",
            new WithdrawMoneyRequest(new BigDecimal("150.00"), "BRL"),
            WithdrawMoneyResponse.class);
        assertThat(withdrawResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify account_summary
        Optional<BalanceView> afterWithdraw = accountSummaryRepository.findById(accountId);
        assertThat(afterWithdraw).isPresent();
        BalanceView view = afterWithdraw.get();
        assertThat(view.balance()).isEqualByComparingTo("350.00");
        assertThat(view.totalDeposited()).isEqualByComparingTo("500.00");
        assertThat(view.totalWithdrawn()).isEqualByComparingTo("150.00");
        assertThat(view.transactionCount()).isEqualTo(2);
        assertThat(view.lastEventSequence()).isEqualTo(3L);

        // Verify transaction_history has 2 rows (deposit + withdraw)
        int historyCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transaction_history WHERE account_id = ?",
            Integer.class, accountId);
        assertThat(historyCount).isEqualTo(2);
    }

    // ── Idempotency: same event replayed does not duplicate state ───────────────

    @Test
    @DisplayName("rebuild: delete and replay events produces identical state")
    void rebuild_deletesAndRebuildsFromEventStore() {
        // Create account and deposit
        ResponseEntity<CreateAccountResponse> createResp = restTemplate.postForEntity(
            "/api/v1/accounts",
            new CreateAccountRequest("owner-it-rebuild"),
            CreateAccountResponse.class);
        UUID accountId = createResp.getBody().accountId();

        restTemplate.postForEntity(
            "/api/v1/accounts/" + accountId + "/deposit",
            new DepositMoneyRequest(new BigDecimal("100.00"), "BRL"),
            DepositMoneyResponse.class);

        // Capture state before rebuild
        BalanceView beforeRebuild = accountSummaryRepository.findById(accountId).orElseThrow();
        int historyBeforeRebuild = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transaction_history WHERE account_id = ?",
            Integer.class, accountId);

        // Execute rebuild
        RebuildResult result = accountProjector.rebuild(accountId, eventStoreRepository);

        // Verify rebuild result
        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.rebuiltEvents()).isEqualTo(2);  // AccountCreated + MoneyDeposited

        // Verify state after rebuild is identical to before
        BalanceView afterRebuild = accountSummaryRepository.findById(accountId).orElseThrow();
        assertThat(afterRebuild.balance()).isEqualByComparingTo(beforeRebuild.balance());
        assertThat(afterRebuild.totalDeposited()).isEqualByComparingTo(beforeRebuild.totalDeposited());
        assertThat(afterRebuild.totalWithdrawn()).isEqualByComparingTo(beforeRebuild.totalWithdrawn());
        assertThat(afterRebuild.transactionCount()).isEqualTo(beforeRebuild.transactionCount());

        int historyAfterRebuild = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transaction_history WHERE account_id = ?",
            Integer.class, accountId);
        assertThat(historyAfterRebuild).isEqualTo(historyBeforeRebuild);
    }
}
