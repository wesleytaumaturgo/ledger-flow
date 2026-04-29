package com.wesleytaumaturgo.ledgerflow.command.infrastructure.eventstore;

import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.AccountCreated;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyDeposited;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyWithdrawn;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.TransferCompleted;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Phase 2's concrete DomainEvent subtypes with the DefaultEventDeserializer
 * registry at application startup.
 *
 * Why @PostConstruct: registration must happen ONCE before any PostgresEventStore.load()
 * call deserializes events. @Configuration + @PostConstruct gives application-context-startup
 * ordering — guaranteed before any @Service is invoked.
 *
 * The eventType strings ("AccountCreated", etc.) match event_store.event_type values written
 * by PostgresEventStore.save() via event.getClass().getSimpleName().
 */
@Configuration
public class EventConfig {

    private final DefaultEventDeserializer deserializer;

    public EventConfig(DefaultEventDeserializer deserializer) {
        this.deserializer = deserializer;
    }

    @PostConstruct
    public void registerEventTypes() {
        deserializer.registerEventType("AccountCreated",    AccountCreated.class);
        deserializer.registerEventType("MoneyDeposited",    MoneyDeposited.class);
        deserializer.registerEventType("MoneyWithdrawn",    MoneyWithdrawn.class);
        deserializer.registerEventType("TransferCompleted", TransferCompleted.class);
    }
}
