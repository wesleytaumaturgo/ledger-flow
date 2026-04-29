package com.wesleytaumaturgo.ledgerflow.api;

import com.wesleytaumaturgo.ledgerflow.api.dto.BalanceResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.TransactionsPageResponse;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.BalanceView;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.GetBalanceUseCase;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.GetTransactionHistoryUseCase;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.TransactionFilter;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.TransactionHistoryView;
import jakarta.validation.constraints.Max;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * HTTP adapter for query-side operations on the Account read model.
 *
 * Thin controller (CLAUDE.md §3.1, .claude/rules/controllers.md):
 *   - Zero business logic — delegates entirely to use cases
 *   - Zero @Transactional — transactions belong to use cases (ArchUnit Rule 2)
 *   - Zero @ExceptionHandler — handled by GlobalExceptionHandler (ArchUnit Rule 3)
 *   - Zero imports from command/domain/** (ArchUnit Rule 5)
 *   - @Validated at class level for @Max(50) on size parameter
 *   - X-Trace-Id response header from MDC (set by CorrelationIdFilter)
 *
 * REQ-balance-query / FR-011, REQ-transaction-history / FR-012
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class AccountQueryController {

    private final GetBalanceUseCase getBalanceUseCase;
    private final GetTransactionHistoryUseCase getTransactionHistoryUseCase;

    public AccountQueryController(GetBalanceUseCase getBalanceUseCase,
                                  GetTransactionHistoryUseCase getTransactionHistoryUseCase) {
        this.getBalanceUseCase = getBalanceUseCase;
        this.getTransactionHistoryUseCase = getTransactionHistoryUseCase;
    }

    /**
     * Returns the current balance read model for the given account.
     *
     * @param id account UUID
     * @return 200 with BalanceResponse; 404 ACCOUNT_NOT_FOUND if account does not exist
     */
    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID id) {
        BalanceView view = getBalanceUseCase.execute(id);
        String traceId = MDC.get("traceId");
        return ResponseEntity.ok()
            .header("X-Trace-Id", traceId != null ? traceId : "")
            .body(BalanceResponse.from(view));
    }

    /**
     * Returns paginated transaction history for the given account.
     *
     * Query parameters:
     *   type  — optional event type filter (DEPOSIT, WITHDRAWAL, TRANSFER_OUT, TRANSFER_IN)
     *   from  — optional ISO-8601 instant lower bound (inclusive)
     *   to    — optional ISO-8601 instant upper bound (inclusive)
     *   page  — page number (0-indexed), default 0
     *   size  — page size, default 20, maximum 50
     *
     * @param id   account UUID
     * @param type optional event type filter
     * @param from optional lower bound (ISO-8601 instant string parsed by Spring)
     * @param to   optional upper bound (ISO-8601 instant string parsed by Spring)
     * @param page page number (0-indexed)
     * @param size page size; @Max(50) triggers ConstraintViolationException → 400
     * @return 200 with TransactionsPageResponse; 404 ACCOUNT_NOT_FOUND if account missing
     */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<TransactionsPageResponse> getTransactions(
            @PathVariable UUID id,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Max(50) int size) {

        TransactionFilter filter = new TransactionFilter(from, to, type);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("occurredAt").descending());
        Page<TransactionHistoryView> result =
            getTransactionHistoryUseCase.execute(id, filter, pageable);

        String traceId = MDC.get("traceId");
        return ResponseEntity.ok()
            .header("X-Trace-Id", traceId != null ? traceId : "")
            .body(TransactionsPageResponse.from(result));
    }
}
