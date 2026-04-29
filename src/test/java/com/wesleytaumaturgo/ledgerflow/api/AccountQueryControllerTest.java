package com.wesleytaumaturgo.ledgerflow.api;

import com.wesleytaumaturgo.ledgerflow.api.dto.BalanceResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.TransactionsPageResponse;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.BalanceView;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.GetBalanceUseCase;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.GetTransactionHistoryUseCase;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.TransactionFilter;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.TransactionHistoryView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice test for AccountQueryController.
 *
 * Thin controller rules (CLAUDE.md §3.1, controllers.md):
 *   - Zero business logic verified via mocked use cases
 *   - No @ExceptionHandler in controller (ArchUnit Rule 3)
 *   - @Validated + @Max(50) on size parameter enforces 400 for size > 50
 *   - X-Trace-Id header populated from MDC.get("traceId")
 *
 * GET /api/v1/accounts/{id}/balance — REQ-balance-query / FR-011
 * GET /api/v1/accounts/{id}/transactions — REQ-transaction-history / FR-012
 */
@WebMvcTest(AccountQueryController.class)
class AccountQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetBalanceUseCase getBalanceUseCase;

    @MockBean
    private GetTransactionHistoryUseCase getTransactionHistoryUseCase;

    // ── GET /api/v1/accounts/{id}/balance ──────────────────────────────────────

    @Test
    @DisplayName("GET /balance returns 200 with all balance fields for existing account")
    void getBalance_existingAccount_returns200WithBalanceShape() throws Exception {
        UUID accountId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        BalanceView view = new BalanceView(
            accountId,
            "owner-1",
            new BigDecimal("100.00"),
            "BRL",
            3,
            3L,
            new BigDecimal("150.00"),
            new BigDecimal("50.00"),
            Instant.parse("2026-01-01T00:00:00Z")
        );
        when(getBalanceUseCase.execute(accountId)).thenReturn(view);

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId.toString()))
            .andExpect(jsonPath("$.balance").value(100.00))
            .andExpect(jsonPath("$.currency").value("BRL"))
            .andExpect(jsonPath("$.transactionCount").value(3))
            .andExpect(jsonPath("$.lastTransactionAt").exists());
    }

    @Test
    @DisplayName("GET /balance returns 404 ACCOUNT_NOT_FOUND for missing account")
    void getBalance_missingAccount_returns404WithProblemDetail() throws Exception {
        UUID accountId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(getBalanceUseCase.execute(accountId))
            .thenThrow(new AccountNotFoundException(accountId));

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /balance with invalid UUID path variable returns 400")
    void getBalance_invalidUUID_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/balance", "not-a-uuid"))
            .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/accounts/{id}/transactions ─────────────────────────────────

    @Test
    @DisplayName("GET /transactions returns 200 with pagination metadata for existing account")
    void getTransactions_existingAccount_returns200WithPagination() throws Exception {
        UUID accountId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        TransactionHistoryView txView = new TransactionHistoryView(
            UUID.randomUUID(), accountId, "DEPOSIT",
            new BigDecimal("100.00"), "BRL", "Deposit",
            Instant.parse("2026-01-01T00:00:00Z"), null
        );
        PageImpl<TransactionHistoryView> page =
            new PageImpl<>(List.of(txView), PageRequest.of(0, 20), 1L);

        when(getTransactionHistoryUseCase.execute(eq(accountId), any(TransactionFilter.class), any()))
            .thenReturn(page);

        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].eventType").value("DEPOSIT"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /transactions with size=51 returns 400")
    void getTransactions_sizeTooLarge_returns400() throws Exception {
        UUID accountId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", accountId)
                .param("size", "51"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /transactions with type=DEPOSIT passes filter to use case")
    void getTransactions_withTypeFilter_passesFilterToUseCase() throws Exception {
        UUID accountId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        PageImpl<TransactionHistoryView> page =
            new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L);

        when(getTransactionHistoryUseCase.execute(eq(accountId), any(TransactionFilter.class), any()))
            .thenReturn(page);

        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", accountId)
                .param("type", "DEPOSIT"))
            .andExpect(status().isOk());

        verify(getTransactionHistoryUseCase).execute(
            eq(accountId),
            eq(new TransactionFilter(null, null, "DEPOSIT")),
            any()
        );
    }
}
