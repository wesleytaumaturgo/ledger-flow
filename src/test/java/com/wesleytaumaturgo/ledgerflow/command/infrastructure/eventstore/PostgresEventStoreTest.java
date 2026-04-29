package com.wesleytaumaturgo.ledgerflow.command.infrastructure.eventstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.AccountCreated;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostgresEventStoreTest {

    private JdbcTemplate jdbc;
    private ApplicationEventPublisher publisher;
    private EventDeserializer eventDeserializer;
    private MeterRegistry meterRegistry;
    private PostgresEventStore eventStore;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        publisher = mock(ApplicationEventPublisher.class);
        eventDeserializer = mock(EventDeserializer.class);
        meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        eventStore = new PostgresEventStore(jdbc, objectMapper, publisher, eventDeserializer, meterRegistry);
    }

    @Test
    @DisplayName("save increments events.stored.total by the number of events after successful INSERTs")
    void save_incrementsEventsStoredTotal_byEventCount() {
        UUID aggregateId = UUID.randomUUID();
        DomainEvent event1 = anAccountCreated(aggregateId, 1L);
        DomainEvent event2 = anAccountCreated(aggregateId, 2L);
        when(jdbc.update(any(String.class), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(1);

        eventStore.save(aggregateId, "Account", List.of(event1, event2));

        assertThat(meterRegistry.counter("events.stored.total", "aggregate_type", "Account").count())
            .isEqualTo(2.0);
    }

    @Test
    @DisplayName("save with empty event list does not increment events.stored.total")
    void save_emptyList_doesNotIncrementCounter() {
        UUID aggregateId = UUID.randomUUID();

        eventStore.save(aggregateId, "Account", List.of());

        assertThat(meterRegistry.counter("events.stored.total", "aggregate_type", "Account").count())
            .isEqualTo(0.0);
    }

    private static AccountCreated anAccountCreated(UUID accountId, long sequenceNumber) {
        return new AccountCreated(UUID.randomUUID(), accountId, "owner-test",
            Instant.parse("2026-01-01T10:00:00Z"), sequenceNumber);
    }
}
