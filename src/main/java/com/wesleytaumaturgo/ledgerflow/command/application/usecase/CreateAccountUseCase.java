package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import com.wesleytaumaturgo.ledgerflow.command.domain.model.Account;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.AccountId;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.EventStoreRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a new Account aggregate. REQ-account-create / FR-001.
 *
 * No retry loop (D-04): sequence_number=1 is unique-per-aggregate by definition;
 * the new aggregate has a freshly generated AccountId, so no concurrent write to the same
 * (aggregate_id, sequence_number) pair can exist.
 *
 * @Transactional applies directly to execute() — single atomic operation.
 */
@Service
public class CreateAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateAccountUseCase.class);
    private static final String AGGREGATE_TYPE = "Account";

    private final EventStoreRepository eventStore;
    private final Counter createCounter;
    private final Counter commandsExecutedCounter;
    private final Timer createTimer;

    public CreateAccountUseCase(EventStoreRepository eventStore, MeterRegistry meterRegistry) {
        this.eventStore = eventStore;
        this.createCounter = meterRegistry.counter("account.command.create.total");
        this.commandsExecutedCounter = meterRegistry.counter(
            "commands_executed_total", "command_type", "CreateAccount");
        this.createTimer = meterRegistry.timer("usecase.execution.duration", "usecase", "create_account");
    }

    @Transactional
    public CreateAccountResult execute(CreateAccountCommand command) {
        return createTimer.record(() -> {
            AccountId id = AccountId.generate();
            Account account = Account.createAccount(id, command.ownerId());
            eventStore.save(id.value(), AGGREGATE_TYPE, account.pullDomainEvents());
            createCounter.increment();
            commandsExecutedCounter.increment();
            log.debug("Account created: {}", id.value());
            return new CreateAccountResult(id.value());
        });
    }
}
