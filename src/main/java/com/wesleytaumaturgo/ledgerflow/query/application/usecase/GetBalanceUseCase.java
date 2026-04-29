package com.wesleytaumaturgo.ledgerflow.query.application.usecase;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.AccountSummaryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Query use case: retrieves the current balance view for a given account.
 *
 * Reads from account_summary read model only — never touches event_store (CQRS principle).
 * Records Timer metric "query.balance.duration" with outcome tag: success | not_found.
 *
 * @Transactional(readOnly = true) is mandatory per FORGE §4.1 for all query use cases.
 */
@Service
public class GetBalanceUseCase {

    private final AccountSummaryRepository repository;
    private final MeterRegistry meterRegistry;

    public GetBalanceUseCase(AccountSummaryRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public BalanceView execute(UUID accountId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            BalanceView view = repository.findById(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            sample.stop(Timer.builder("query.balance.duration")
                    .tag("outcome", "success")
                    .register(meterRegistry));
            return view;
        } catch (AccountNotFoundException e) {
            sample.stop(Timer.builder("query.balance.duration")
                    .tag("outcome", "not_found")
                    .register(meterRegistry));
            throw e;
        }
    }
}
