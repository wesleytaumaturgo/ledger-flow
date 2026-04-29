package com.wesleytaumaturgo.ledgerflow.command.infrastructure.eventstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.OptimisticLockException;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.AccountCreated;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("save increments events.stored by the number of events after successful INSERTs")
    void save_incrementsEventsStored_byEventCount() {
        UUID aggregateId = UUID.randomUUID();
        DomainEvent event1 = anAccountCreated(aggregateId, 1L);
        DomainEvent event2 = anAccountCreated(aggregateId, 2L);
        when(jdbc.update(any(String.class), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(1);

        eventStore.save(aggregateId, "Account", List.of(event1, event2));

        assertThat(meterRegistry.counter("events.stored", "aggregate_type", "Account").count())
            .isEqualTo(2.0);
    }

    @Test
    @DisplayName("save with empty event list does not increment events.stored")
    void save_emptyList_doesNotIncrementCounter() {
        UUID aggregateId = UUID.randomUUID();

        eventStore.save(aggregateId, "Account", List.of());

        assertThat(meterRegistry.counter("events.stored", "aggregate_type", "Account").count())
            .isEqualTo(0.0);
    }

    @Test
    @DisplayName("save on OptimisticLockException does NOT increment events.stored counter")
    void save_optimisticLockException_doesNotIncrementCounter() {
        UUID aggregateId = UUID.randomUUID();
        DomainEvent event = anAccountCreated(aggregateId, 1L);
        when(jdbc.update(any(String.class), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new DuplicateKeyException("UNIQUE constraint violation"));

        assertThatThrownBy(() -> eventStore.save(aggregateId, "Account", List.of(event)))
            .isInstanceOf(OptimisticLockException.class);

        assertThat(meterRegistry.counter("events.stored", "aggregate_type", "Account").count())
            .isEqualTo(0.0);
    }

    @Test
    @DisplayName("load records replay.duration Timer per invocation")
    void load_recordsTimer_onInvocation() {
        UUID aggregateId = UUID.randomUUID();
        when(jdbc.query(any(String.class), any(RowMapper.class), any()))
            .thenReturn(List.of());

        eventStore.load(aggregateId);

        assertThat(meterRegistry.get("replay.duration")
            .tags("aggregate_type", "Account")
            .timer().count())
            .isEqualTo(1L);
    }

    @Test
    @DisplayName("load records replay.duration Timer even when DataAccessException is thrown")
    void load_recordsTimer_evenOnException() {
        UUID aggregateId = UUID.randomUUID();
        when(jdbc.query(any(String.class), any(RowMapper.class), any()))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("DB error"));

        assertThatThrownBy(() -> eventStore.load(aggregateId))
            .isInstanceOf(Exception.class);

        assertThat(meterRegistry.get("replay.duration")
            .tags("aggregate_type", "Account")
            .timer().count())
            .isEqualTo(1L);
    }

    private static AccountCreated anAccountCreated(UUID accountId, long sequenceNumber) {
        return new AccountCreated(UUID.randomUUID(), accountId, "owner-test",
            Instant.parse("2026-01-01T10:00:00Z"), sequenceNumber);
    }
}
