package com.wesleytaumaturgo.ledgerflow.query.application.usecase;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.EventStoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Query use case: loads all events for an account from the event store,
 * ordered by sequence_number ASC.
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
        List<DomainEvent> events = eventStoreRepository.load(accountId);
        if (events.isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }
        return events.stream()
                .map(e -> new EventHistoryView(
                        e.eventId(),
                        e.getClass().getSimpleName(),
                        e.sequenceNumber(),
                        e.occurredAt()))
                .toList();
    }
}
