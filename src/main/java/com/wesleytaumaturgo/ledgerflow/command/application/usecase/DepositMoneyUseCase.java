package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.OptimisticLockException;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.Account;
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
 * Deposits a positive monetary amount. REQ-money-deposit / FR-002.
 *
 * Retry pattern (D-03/D-04/D-05):
 *   - execute() — NO @Transactional. Manages the retry loop. Catches OptimisticLockException
 *     and re-attempts up to {@code commandProperties.maxRetries()} times.
 *   - doExecute() — @Transactional. One atomic attempt: load -> reconstitute -> deposit -> save.
 *     Called via Spring proxy through {@code self} field (NOT this.doExecute) so @Transactional
 *     is honored.
 *   - AccountNotFoundException and InvalidAmountException are NOT retried — they are permanent
 *     domain failures. Only OptimisticLockException is retried.
 */
@Service
public class DepositMoneyUseCase {

    private static final Logger log = LoggerFactory.getLogger(DepositMoneyUseCase.class);
    private static final String AGGREGATE_TYPE = "Account";

    private final EventStoreRepository eventStore;
    private final CommandProperties properties;
    private final Counter depositCounter;
    private final Counter retryCounter;
    private final Counter exhaustedCounter;
    private final Counter commandsExecutedCounter;

    /**
     * Self-injection (D-05): @Lazy avoids circular-dependency BeanCreationException at startup.
     * Calling self.doExecute(...) goes through the Spring proxy so @Transactional is applied;
     * calling this.doExecute(...) bypasses the proxy and silently disables the transaction.
     */
    @Autowired
    @Lazy
    private DepositMoneyUseCase self;

    @Autowired
    public DepositMoneyUseCase(EventStoreRepository eventStore,
                                CommandProperties properties,
                                MeterRegistry meterRegistry) {
        this.eventStore = eventStore;
        this.properties = properties;
        this.depositCounter   = meterRegistry.counter("account.command.deposit.total");
        this.retryCounter     = meterRegistry.counter("account.command.retry.total", "operation", "deposit");
        this.exhaustedCounter = meterRegistry.counter("account.command.optimistic_lock_exhausted.total", "operation", "deposit");
        this.commandsExecutedCounter = meterRegistry.counter(
            "commands_executed_total", "command_type", "DepositMoney");
    }

    /**
     * Constructor used by unit tests to inject a self reference directly without Spring AOP.
     * Production code uses the field injection via @Autowired @Lazy.
     */
    DepositMoneyUseCase(EventStoreRepository eventStore,
                         CommandProperties properties,
                         MeterRegistry meterRegistry,
                         DepositMoneyUseCase self) {
        this(eventStore, properties, meterRegistry);
        this.self = self;
    }

    // No @Transactional here — execute manages the retry loop OUTSIDE any transaction.
    public DepositMoneyResult execute(DepositMoneyCommand cmd) {
        int attempt = 0;
        while (true) {
            try {
                DepositMoneyResult result = self.doExecute(cmd);
                commandsExecutedCounter.increment();
                return result;
            } catch (OptimisticLockException e) {
                attempt++;
                if (attempt >= properties.maxRetries()) {
                    exhaustedCounter.increment();
                    log.warn("Deposit retry exhausted for account {} after {} attempts",
                        cmd.accountId(), attempt);
                    throw e;
                }
                retryCounter.increment();
                log.debug("Deposit retry {}/{} for account {}",
                    attempt, properties.maxRetries(), cmd.accountId());
            }
        }
    }

    @Transactional
    public DepositMoneyResult doExecute(DepositMoneyCommand cmd) {
        List<DomainEvent> events = eventStore.load(cmd.accountId());
        if (events.isEmpty()) {
            throw new AccountNotFoundException(cmd.accountId());
        }
        Account account = Account.reconstitute(events);
        account.deposit(Money.of(cmd.amount(), cmd.currency()));
        eventStore.save(cmd.accountId(), AGGREGATE_TYPE, account.pullDomainEvents());
        depositCounter.increment();
        return new DepositMoneyResult(
            cmd.accountId(),
            account.balance().amount(),
            account.balance().currency());
    }
}
