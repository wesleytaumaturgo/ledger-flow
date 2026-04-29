package com.wesleytaumaturgo.ledgerflow.query.application.usecase;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.AccountSummaryRepository;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.TransactionHistoryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Query use case: retrieves paginated transaction history for a given account.
 *
 * <p>Validates account existence via AccountSummaryRepository first — avoids a
 * misleading empty page for non-existent accounts. Then fetches rows from
 * TransactionHistoryRepository using optional filter criteria (eventType, from, to).
 *
 * <p>Max page size (50) is validated at the controller layer via {@code @Max(50)},
 * not here. A page number beyond the result total returns an empty Page — HTTP 200.
 *
 * <p>Timer {@code query.transactions.duration} with tag {@code outcome=success|not_found}
 * is recorded via Micrometer on every invocation.
 *
 * @Transactional(readOnly = true) is mandatory per FORGE §4.1 for all query use cases.
 */
@Service
public class GetTransactionHistoryUseCase {

    private final TransactionHistoryRepository historyRepository;
    private final AccountSummaryRepository summaryRepository;
    private final MeterRegistry meterRegistry;

    public GetTransactionHistoryUseCase(
            TransactionHistoryRepository historyRepository,
            AccountSummaryRepository summaryRepository,
            MeterRegistry meterRegistry) {
        this.historyRepository = historyRepository;
        this.summaryRepository = summaryRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Returns a page of transaction history for the given account.
     *
     * @param accountId the account to query — must exist in account_summary
     * @param filter    optional filter criteria; null fields mean no filter applied
     * @param pageable  pagination parameters (page number, page size)
     * @return page of transaction views; empty page if page number exceeds total
     * @throws AccountNotFoundException if no account summary exists for accountId
     */
    @Transactional(readOnly = true)
    public Page<TransactionHistoryView> execute(
            UUID accountId,
            TransactionFilter filter,
            Pageable pageable) {

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // 1. Validate account exists — avoids misleading empty page for non-existent accounts
            summaryRepository.findById(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));

            // 2. Fetch paginated history with optional filters.
            //    Domain interface uses (int page, int size) primitives — convert from Pageable.
            int page = pageable.getPageNumber();
            int size = pageable.getPageSize();

            List<TransactionHistoryView> content =
                    historyRepository.findByAccountId(accountId, filter, page, size);
            long total = historyRepository.countByAccountId(accountId, filter);

            sample.stop(Timer.builder("query.transactions.duration")
                    .tag("outcome", "success")
                    .register(meterRegistry));

            return new PageImpl<>(content, pageable, total);

        } catch (AccountNotFoundException e) {
            sample.stop(Timer.builder("query.transactions.duration")
                    .tag("outcome", "not_found")
                    .register(meterRegistry));
            throw e;
        }
    }
}
