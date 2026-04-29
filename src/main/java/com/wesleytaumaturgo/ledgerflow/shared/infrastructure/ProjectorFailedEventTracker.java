package com.wesleytaumaturgo.ledgerflow.shared.infrastructure;

import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.AccountCreated;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyDeposited;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyWithdrawn;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.TransferCompleted;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of projector failures.
 * Thread-safe via ConcurrentHashMap.
 * Used by AccountProjector to record events that could not be processed.
 * Exposed via admin endpoint for observability and retry.
 */
@Component
public class ProjectorFailedEventTracker {

    private final ConcurrentHashMap<UUID, FailedEventRecord> failed = new ConcurrentHashMap<>();

    /**
     * Records a failed event. Subsequent failures for the same eventId overwrite the entry.
     */
    public void record(DomainEvent event, Exception e) {
        String accountId = extractAccountId(event);
        failed.put(event.eventId(), new FailedEventRecord(
            event.eventId(),
            event.getClass().getSimpleName(),
            accountId,
            Instant.now(),
            e.getMessage()
        ));
    }

    /**
     * Returns the number of distinct failed events tracked since startup.
     */
    public int size() {
        return failed.size();
    }

    /**
     * Returns all failed event records. Snapshot — not live view.
     */
    public Collection<FailedEventRecord> getAll() {
        return failed.values();
    }

    /**
     * Removes a failed event record (e.g., after successful retry).
     */
    public void remove(UUID eventId) {
        failed.remove(eventId);
    }

    private String extractAccountId(DomainEvent event) {
        return switch (event) {
            case AccountCreated e -> e.accountId().toString();
            case MoneyDeposited e -> e.accountId().toString();
            case MoneyWithdrawn e -> e.accountId().toString();
            case TransferCompleted e -> e.accountId().toString();
            default -> "unknown";
        };
    }
}
