package com.wesleytaumaturgo.ledgerflow.command.infrastructure.eventstore;

import com.wesleytaumaturgo.ledgerflow.IntegrationTestBase;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.OptimisticLockException;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.EventStoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that concurrent writes to the same (aggregate_id, sequence_number) pair
 * result in exactly 1 success and N-1 OptimisticLockException throws.
 *
 * Verifies DEC-008: UNIQUE(aggregate_id, sequence_number) is the sole concurrency
 * control mechanism. Uses CountDownLatch to maximize concurrency — all threads
 * attempt the write simultaneously before any can commit.
 */
class ConcurrencyIT extends IntegrationTestBase {

    private static final int THREAD_COUNT = 5;

    @Autowired
    private EventStoreRepository eventStoreRepository;

    /**
     * Minimal DomainEvent stub for Phase 1 testing.
     * Phase 2 registers real event types in DefaultEventDeserializer.
     * This record is NOT a production event class — used only to supply save() with a
     * valid DomainEvent payload for the purpose of exercising the UNIQUE constraint.
     */
    private record TestEvent(UUID eventId, Instant occurredAt, long sequenceNumber)
            implements DomainEvent {}

    @Test
    @DisplayName("Concurrent writes to same (aggregate_id, sequence_number): exactly 1 succeeds, N-1 throw OptimisticLockException")
    void concurrent_writes_to_same_sequence_number_result_in_exactly_one_success() throws InterruptedException {
        // Arrange
        UUID aggregateId = UUID.randomUUID();
        // All threads will try to write sequence_number = 1 for the same aggregate.
        // The UNIQUE(aggregate_id, sequence_number) constraint ensures only one can succeed.
        long conflictingSequence = 1L;

        CountDownLatch startGate = new CountDownLatch(1);        // holds all threads until released
        CountDownLatch endGate   = new CountDownLatch(THREAD_COUNT); // waits for all threads to finish

        AtomicInteger successCount  = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // Block until all threads are ready for simultaneous release
                    DomainEvent event = new TestEvent(
                        UUID.randomUUID(),
                        Instant.now(),
                        conflictingSequence  // Same sequence_number on same aggregate = conflict
                    );
                    eventStoreRepository.save(aggregateId, "TestAggregate", List.of(event));
                    successCount.incrementAndGet();
                } catch (OptimisticLockException e) {
                    conflictCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Act — release all threads simultaneously to maximize concurrency
        startGate.countDown();
        endGate.await();
        executor.shutdown();

        // Assert — exactly 1 winner, THREAD_COUNT-1 conflicts
        assertThat(successCount.get())
            .as("Exactly one thread should succeed in inserting (aggregate_id, sequence_number=1)")
            .isEqualTo(1);
        assertThat(conflictCount.get())
            .as("All other threads should receive OptimisticLockException from UNIQUE constraint violation")
            .isEqualTo(THREAD_COUNT - 1);
        assertThat(successCount.get() + conflictCount.get())
            .as("All threads must have completed — no silent failures swallowed")
            .isEqualTo(THREAD_COUNT);
    }
}
