package com.wesleytaumaturgo.ledgerflow.query.application.usecase;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.EventStoreRepository;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.RawEventRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Query use case: loads all events for an account from the event store,
 * ordered by sequence_number ASC, including raw JSONB payload.
 *
 * Uses rawLoad() instead of load() to obtain event payload (eventData, eventMetadata)
 * without deserializing into domain event objects. This keeps the domain layer pure:
 * DomainEvent does not need a payload accessor method.
 *
 * Returns HTTP 404 (AccountNotFoundException) when the aggregate has no events
 * — an empty event list means the account does not exist in the event store.
 *
 * @Transactional(readOnly = true) is mandatory per FORGE §4.1 for query use cases.
 */
@Service
public class GetEventHistoryUseCase {

    private final EventStoreRepository eventStoreRepository;

    public GetEventHistoryUseCase(EventStoreRepository eventStoreRepository) {
        this.eventStoreRepository = eventStoreRepository;
    }

    @Transactional(readOnly = true)
    public List<EventHistoryView> execute(UUID accountId) {
        List<RawEventRecord> events = eventStoreRepository.rawLoad(accountId);
        if (events.isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }
        return events.stream()
                .map(e -> new EventHistoryView(
                        e.eventId(),
                        e.eventType(),
                        e.eventData(),
                        e.eventMetadata(),
                        e.sequenceNumber(),
                        e.occurredAt()))
                .toList();
    }
}
