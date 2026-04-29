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
 * Withdraws a positive monetary amount. REQ-money-withdraw / FR-003 + REQ-reject-overdraft / FR-005.
 * Same retry pattern as DepositMoneyUseCase (D-03/D-04/D-05).
 * InsufficientFundsException propagates without retry — domain failure is permanent.
 */
@Service
public class WithdrawMoneyUseCase {

    private static final Logger log = LoggerFactory.getLogger(WithdrawMoneyUseCase.class);
    private static final String AGGREGATE_TYPE = "Account";

    private final EventStoreRepository eventStore;
    private final CommandProperties properties;
    private final Counter withdrawCounter;
    private final Counter retryCounter;
    private final Counter exhaustedCounter;

    @Autowired
    @Lazy
    private WithdrawMoneyUseCase self;

    @Autowired
    public WithdrawMoneyUseCase(EventStoreRepository eventStore,
                                 CommandProperties properties,
                                 MeterRegistry meterRegistry) {
        this.eventStore = eventStore;
        this.properties = properties;
        this.withdrawCounter  = meterRegistry.counter("account.command.withdraw.total");
        this.retryCounter     = meterRegistry.counter("account.command.retry.total", "operation", "withdraw");
        this.exhaustedCounter = meterRegistry.counter("account.command.optimistic_lock_exhausted.total", "operation", "withdraw");
    }

    WithdrawMoneyUseCase(EventStoreRepository eventStore,
                          CommandProperties properties,
                          MeterRegistry meterRegistry,
                          WithdrawMoneyUseCase self) {
        this(eventStore, properties, meterRegistry);
        this.self = self;
    }

    public WithdrawMoneyResult execute(WithdrawMoneyCommand cmd) {
        int attempt = 0;
        while (true) {
            try {
                return self.doExecute(cmd);
            } catch (OptimisticLockException e) {
                attempt++;
                if (attempt >= properties.maxRetries()) {
                    exhaustedCounter.increment();
                    log.warn("Withdraw retry exhausted for account {} after {} attempts",
                        cmd.accountId(), attempt);
                    throw e;
                }
                retryCounter.increment();
                log.debug("Withdraw retry {}/{} for account {}",
                    attempt, properties.maxRetries(), cmd.accountId());
            }
        }
    }

    @Transactional
    public WithdrawMoneyResult doExecute(WithdrawMoneyCommand cmd) {
        List<DomainEvent> events = eventStore.load(cmd.accountId());
        if (events.isEmpty()) {
            throw new AccountNotFoundException(cmd.accountId());
        }
        Account account = Account.reconstitute(events);
        account.withdraw(Money.of(cmd.amount(), cmd.currency()));
        eventStore.save(cmd.accountId(), AGGREGATE_TYPE, account.pullDomainEvents());
        withdrawCounter.increment();
        return new WithdrawMoneyResult(
            cmd.accountId(),
            account.balance().amount(),
            account.balance().currency());
    }
}
