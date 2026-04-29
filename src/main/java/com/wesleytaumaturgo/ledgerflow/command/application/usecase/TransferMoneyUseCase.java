package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountInactiveException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.OptimisticLockException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.SelfTransferNotAllowedException;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.Account;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.AccountId;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.Money;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.EventStoreRepository;
import com.wesleytaumaturgo.ledgerflow.command.infrastructure.config.CommandProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Transfers funds between two accounts. REQ-money-transfer / FR-004 + REQ-reject-inactive-transfer / FR-007.
 *
 * Validation order in doExecute (per CONTEXT.md specifics line 117 + D-02):
 *   1. Self-transfer check  — fails fast BEFORE any DB I/O
 *   2. Load source events
 *   3. Load target events
 *   4. Validate source.isActive() (D-02 — always true in MVP, validation path present for post-MVP)
 *   5. Validate target.isActive()
 *   6. account.debitTransfer  — internally validates balance
 *   7. account.creditTransfer
 *   8. Persist source events, then target events — both within same @Transactional
 *
 * If either save throws OptimisticLockException, the entire transaction rolls back (no partial transfer).
 * The retry loop re-attempts the WHOLE transfer, including loading the latest events.
 */
@Service
public class TransferMoneyUseCase {

    private static final Logger log = LoggerFactory.getLogger(TransferMoneyUseCase.class);
    private static final String AGGREGATE_TYPE = "Account";

    private final EventStoreRepository eventStore;
    private final CommandProperties properties;
    private final Counter transferCounter;
    private final Counter retryCounter;
    private final Counter exhaustedCounter;
    private final Counter commandsExecutedCounter;

    @Autowired
    @Lazy
    private TransferMoneyUseCase self;

    @Autowired
    public TransferMoneyUseCase(EventStoreRepository eventStore,
                                 CommandProperties properties,
                                 MeterRegistry meterRegistry) {
        this.eventStore = eventStore;
        this.properties = properties;
        this.transferCounter  = meterRegistry.counter("account.command.transfer.total");
        this.retryCounter     = meterRegistry.counter("account.command.retry.total", "operation", "transfer");
        this.exhaustedCounter = meterRegistry.counter("account.command.optimistic_lock_exhausted.total", "operation", "transfer");
        this.commandsExecutedCounter = meterRegistry.counter(
            "commands_executed_total", "command_type", "TransferMoney");
    }

    TransferMoneyUseCase(EventStoreRepository eventStore,
                          CommandProperties properties,
                          MeterRegistry meterRegistry,
                          TransferMoneyUseCase self) {
        this(eventStore, properties, meterRegistry);
        this.self = self;
    }

    public TransferMoneyResult execute(TransferMoneyCommand cmd) {
        int attempt = 0;
        while (true) {
            try {
                TransferMoneyResult result = self.doExecute(cmd);
                commandsExecutedCounter.increment();
                return result;
            } catch (OptimisticLockException e) {
                attempt++;
                if (attempt >= properties.maxRetries()) {
                    exhaustedCounter.increment();
                    log.warn("Transfer retry exhausted source={} target={} after {} attempts",
                        cmd.sourceAccountId(), cmd.targetAccountId(), attempt);
                    throw e;
                }
                retryCounter.increment();
                log.debug("Transfer retry {}/{} source={} target={}",
                    attempt, properties.maxRetries(), cmd.sourceAccountId(), cmd.targetAccountId());
            }
        }
    }

    @Transactional
    public TransferMoneyResult doExecute(TransferMoneyCommand cmd) {
        // 1. Self-transfer check BEFORE any DB I/O
        if (cmd.sourceAccountId().equals(cmd.targetAccountId())) {
            throw new SelfTransferNotAllowedException(cmd.sourceAccountId());
        }

        // 2. Load source
        List<DomainEvent> sourceEvents = eventStore.load(cmd.sourceAccountId());
        if (sourceEvents.isEmpty()) {
            throw new AccountNotFoundException(cmd.sourceAccountId());
        }

        // 3. Load target
        List<DomainEvent> targetEvents = eventStore.load(cmd.targetAccountId());
        if (targetEvents.isEmpty()) {
            throw new AccountNotFoundException(cmd.targetAccountId());
        }

        // 4. Reconstitute both
        Account source = Account.reconstitute(sourceEvents);
        Account target = Account.reconstitute(targetEvents);

        // 5. Validate active status (D-02 — always true in MVP, validation path present)
        if (!source.isActive()) {
            throw new AccountInactiveException(cmd.sourceAccountId());
        }
        if (!target.isActive()) {
            throw new AccountInactiveException(cmd.targetAccountId());
        }

        // 6 + 7. Business operations — debit source, credit target
        Money money = Money.of(cmd.amount(), cmd.currency());
        source.debitTransfer(money, AccountId.of(cmd.targetAccountId()));
        target.creditTransfer(money, AccountId.of(cmd.sourceAccountId()));

        // 8. Persist both within the same transaction
        eventStore.save(cmd.sourceAccountId(), AGGREGATE_TYPE, source.pullDomainEvents());
        eventStore.save(cmd.targetAccountId(), AGGREGATE_TYPE, target.pullDomainEvents());

        transferCounter.increment();
        return new TransferMoneyResult(
            cmd.sourceAccountId(),
            cmd.targetAccountId(),
            cmd.amount(),
            cmd.currency());
    }
}
