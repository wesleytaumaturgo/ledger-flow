package com.wesleytaumaturgo.ledgerflow.query.application.projectors;

import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.AccountCreated;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyDeposited;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyWithdrawn;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.TransferCompleted;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.TransferDirection;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.EventStoreRepository;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.BalanceView;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.AccountSummaryData;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.AccountSummaryRepository;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.TransactionHistoryData;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.TransactionHistoryRepository;
import com.wesleytaumaturgo.ledgerflow.shared.infrastructure.ProjectorFailedEventTracker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CQRS projector for the account_summary and transaction_history read models.
 *
 * Listens to domain events published by the command side (PostgresEventStore via
 * ApplicationEventPublisher) and updates the query-side read models.
 *
 * Rules:
 *   - NEVER throws from on() methods — catches all, logs ERROR, tracks in failedEventTracker
 *   - Idempotency: skips event if existing.lastEventSequence() >= event.sequenceNumber()
 *   - Metrics: projector.event.processed (Counter, tags: eventType, outcome=success/skipped/failed)
 *              projector.failed.events.count (Gauge)
 *
 * Location: query/application/projectors/ per CLAUDE.md cqrs-projector rule.
 */
@Component
public class AccountProjector {

    private static final Logger log = LoggerFactory.getLogger(AccountProjector.class);

    private final AccountSummaryRepository summaryRepository;
    private final TransactionHistoryRepository historyRepository;
    private final ProjectorFailedEventTracker failedEventTracker;
    private final MeterRegistry meterRegistry;

    private final EventStoreRepository eventStoreRepository;

    public AccountProjector(AccountSummaryRepository summaryRepository,
                             TransactionHistoryRepository historyRepository,
                             ProjectorFailedEventTracker failedEventTracker,
                             MeterRegistry meterRegistry,
                             EventStoreRepository eventStoreRepository) {
        this.summaryRepository = summaryRepository;
        this.historyRepository = historyRepository;
        this.failedEventTracker = failedEventTracker;
        this.meterRegistry = meterRegistry;
        this.eventStoreRepository = eventStoreRepository;

        // Gauge: tracks total failed events since startup
        Gauge.builder("projector.failed.events.count", failedEventTracker, ProjectorFailedEventTracker::size)
            .description("Number of domain events that failed projector processing since startup")
            .register(meterRegistry);
    }

    // ── AccountCreated ──────────────────────────────────────────────────────────

    @EventListener
    @Transactional
    public void on(AccountCreated event) {
        try {
            Optional<BalanceView> existing = summaryRepository.findById(event.accountId());
            if (isAlreadyProcessed(existing, event.sequenceNumber())) {
                recordMetric("AccountCreated", "skipped");
                log.debug("Skipping duplicate AccountCreated seq={} account={}",
                    event.sequenceNumber(), event.accountId());
                return;
            }

            summaryRepository.save(new AccountSummaryData(
                event.accountId(),
                event.ownerId(),
                BigDecimal.ZERO,
                "BRL",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                event.sequenceNumber(),
                null
            ));
            recordMetric("AccountCreated", "success");
            log.debug("AccountCreated projected: account={}", event.accountId());
        } catch (Exception e) {
            log.error("Projector failed on AccountCreated for {}", event.accountId(), e);
            failedEventTracker.record(event, e);
            recordMetric("AccountCreated", "failed");
        }
    }

    // ── MoneyDeposited ──────────────────────────────────────────────────────────

    @EventListener
    @Transactional
    public void on(MoneyDeposited event) {
        try {
            Optional<BalanceView> existing = summaryRepository.findById(event.accountId());
            if (isAlreadyProcessed(existing, event.sequenceNumber())) {
                recordMetric("MoneyDeposited", "skipped");
                return;
            }

            BalanceView current = existing.orElseThrow(() -> new IllegalStateException(
                "Account summary not found for " + event.accountId()));

            summaryRepository.save(new AccountSummaryData(
                event.accountId(),
                current.ownerId(),
                current.balance().add(event.amount()),
                current.currency(),
                current.totalDeposited().add(event.amount()),
                current.totalWithdrawn(),
                current.transactionCount() + 1,
                event.sequenceNumber(),
                event.occurredAt()
            ));

            historyRepository.save(new TransactionHistoryData(
                event.accountId(),
                "MoneyDeposited",
                event.amount(),
                event.currency(),
                "Deposit",
                event.occurredAt(),
                null,
                event.sequenceNumber()
            ));

            recordMetric("MoneyDeposited", "success");
            log.debug("MoneyDeposited projected: account={} amount={}", event.accountId(), event.amount());
        } catch (Exception e) {
            log.error("Projector failed on MoneyDeposited for {}", event.accountId(), e);
            failedEventTracker.record(event, e);
            recordMetric("MoneyDeposited", "failed");
        }
    }

    // ── MoneyWithdrawn ──────────────────────────────────────────────────────────

    @EventListener
    @Transactional
    public void on(MoneyWithdrawn event) {
        try {
            Optional<BalanceView> existing = summaryRepository.findById(event.accountId());
            if (isAlreadyProcessed(existing, event.sequenceNumber())) {
                recordMetric("MoneyWithdrawn", "skipped");
                return;
            }

            BalanceView current = existing.orElseThrow(() -> new IllegalStateException(
                "Account summary not found for " + event.accountId()));

            summaryRepository.save(new AccountSummaryData(
                event.accountId(),
                current.ownerId(),
                current.balance().subtract(event.amount()),
                current.currency(),
                current.totalDeposited(),
                current.totalWithdrawn().add(event.amount()),
                current.transactionCount() + 1,
                event.sequenceNumber(),
                event.occurredAt()
            ));

            historyRepository.save(new TransactionHistoryData(
                event.accountId(),
                "MoneyWithdrawn",
                event.amount(),
                event.currency(),
                "Withdrawal",
                event.occurredAt(),
                null,
                event.sequenceNumber()
            ));

            recordMetric("MoneyWithdrawn", "success");
            log.debug("MoneyWithdrawn projected: account={} amount={}", event.accountId(), event.amount());
        } catch (Exception e) {
            log.error("Projector failed on MoneyWithdrawn for {}", event.accountId(), e);
            failedEventTracker.record(event, e);
            recordMetric("MoneyWithdrawn", "failed");
        }
    }

    // ── TransferCompleted ───────────────────────────────────────────────────────

    @EventListener
    @Transactional
    public void on(TransferCompleted event) {
        try {
            Optional<BalanceView> existing = summaryRepository.findById(event.accountId());
            if (isAlreadyProcessed(existing, event.sequenceNumber())) {
                recordMetric("TransferCompleted", "skipped");
                return;
            }

            BalanceView current = existing.orElseThrow(() -> new IllegalStateException(
                "Account summary not found for " + event.accountId()));

            BigDecimal newBalance;
            BigDecimal newTotalDeposited = current.totalDeposited();
            BigDecimal newTotalWithdrawn = current.totalWithdrawn();
            String description;

            if (event.direction() == TransferDirection.DEBIT) {
                newBalance = current.balance().subtract(event.amount());
                newTotalWithdrawn = newTotalWithdrawn.add(event.amount());
                description = "Transfer out to " + event.counterpartId();
            } else {
                newBalance = current.balance().add(event.amount());
                newTotalDeposited = newTotalDeposited.add(event.amount());
                description = "Transfer in from " + event.counterpartId();
            }

            summaryRepository.save(new AccountSummaryData(
                event.accountId(),
                current.ownerId(),
                newBalance,
                current.currency(),
                newTotalDeposited,
                newTotalWithdrawn,
                current.transactionCount() + 1,
                event.sequenceNumber(),
                event.occurredAt()
            ));

            historyRepository.save(new TransactionHistoryData(
                event.accountId(),
                "TransferCompleted",
                event.amount(),
                event.currency(),
                description,
                event.occurredAt(),
                event.counterpartId(),
                event.sequenceNumber()
            ));

            recordMetric("TransferCompleted", "success");
            log.debug("TransferCompleted projected: account={} direction={} amount={}",
                event.accountId(), event.direction(), event.amount());
        } catch (Exception e) {
            log.error("Projector failed on TransferCompleted for {}", event.accountId(), e);
            failedEventTracker.record(event, e);
            recordMetric("TransferCompleted", "failed");
        }
    }

    // ── Rebuild ─────────────────────────────────────────────────────────────────

    /**
     * Rebuilds read model for a single account from the injected event store.
     * Called by AdminController — avoids exposing EventStoreRepository to the api layer.
     *
     * @param accountId the account to rebuild
     * @return result containing account ID and number of events replayed
     */
    @Transactional
    public RebuildResult rebuild(UUID accountId) {
        return rebuild(accountId, this.eventStoreRepository);
    }

    /**
     * Rebuilds read model for a single account from the given event store.
     * Deletes existing account_summary and transaction_history rows, then replays
     * all events. Because the summary row is deleted first, idempotency skipping
     * does not apply — all replayed events are processed fresh.
     *
     * @param accountId  the account to rebuild
     * @param eventStore the event store to load events from
     * @return result containing account ID and number of events replayed
     */
    @Transactional
    public RebuildResult rebuild(UUID accountId, EventStoreRepository eventStore) {
        summaryRepository.deleteById(accountId);
        historyRepository.deleteByAccountId(accountId);

        List<DomainEvent> events = eventStore.load(accountId);
        for (DomainEvent event : events) {
            switch (event) {
                case AccountCreated e -> on(e);
                case MoneyDeposited e -> on(e);
                case MoneyWithdrawn e -> on(e);
                case TransferCompleted e -> on(e);
                default -> log.warn("Unknown event during rebuild: {}", event.getClass().getSimpleName());
            }
        }
        log.info("Rebuilt account {} from {} events", accountId, events.size());
        return new RebuildResult(accountId, events.size());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private boolean isAlreadyProcessed(Optional<BalanceView> existing, long eventSequenceNumber) {
        return existing.isPresent() && existing.get().lastEventSequence() >= eventSequenceNumber;
    }

    private void recordMetric(String eventType, String outcome) {
        Counter.builder("projector.event.processed")
            .tag("eventType", eventType)
            .tag("outcome", outcome)
            .description("Number of events processed by the account projector")
            .register(meterRegistry)
            .increment();
    }
}
