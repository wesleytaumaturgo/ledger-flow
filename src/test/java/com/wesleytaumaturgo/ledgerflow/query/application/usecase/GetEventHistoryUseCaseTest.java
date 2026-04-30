package com.wesleytaumaturgo.ledgerflow.query.application.usecase;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.EventStoreRepository;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.RawEventRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetEventHistoryUseCaseTest {

    @Mock
    private EventStoreRepository eventStoreRepository;

    @InjectMocks
    private GetEventHistoryUseCase useCase;

    // ── Returns mapped views when events exist ──────────────────────────────────

    @Test
    @DisplayName("execute returns EventHistoryView list with eventData when events exist")
    void execute_whenEventsExist_returnsMappedViews() {
        UUID accountId = UUID.randomUUID();
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        Instant now = Instant.now();

        RawEventRecord raw1 = stubRawEvent(eventId1, now, 1L);
        RawEventRecord raw2 = stubRawEvent(eventId2, now.plusSeconds(1), 2L);

        when(eventStoreRepository.rawLoad(accountId)).thenReturn(List.of(raw1, raw2));

        List<EventHistoryView> views = useCase.execute(accountId);

        assertThat(views).hasSize(2);

        EventHistoryView first = views.get(0);
        assertThat(first.eventId()).isEqualTo(eventId1);
        assertThat(first.eventType()).isEqualTo("StubEvent");
        assertThat(first.eventData()).isEqualTo("{\"key\":\"value\"}");
        assertThat(first.eventMetadata()).isEqualTo("{}");
        assertThat(first.sequenceNumber()).isEqualTo(1L);
        assertThat(first.occurredAt()).isEqualTo(now);

        EventHistoryView second = views.get(1);
        assertThat(second.eventId()).isEqualTo(eventId2);
        assertThat(second.sequenceNumber()).isEqualTo(2L);
    }

    // ── Throws AccountNotFoundException when no events ──────────────────────────

    @Test
    @DisplayName("execute throws AccountNotFoundException when event list is empty")
    void execute_whenNoEvents_throwsAccountNotFoundException() {
        UUID accountId = UUID.randomUUID();
        when(eventStoreRepository.rawLoad(accountId)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(accountId))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private RawEventRecord stubRawEvent(UUID eventId, Instant occurredAt, long sequenceNumber) {
        return new RawEventRecord(eventId, "StubEvent", "{\"key\":\"value\"}", "{}", sequenceNumber, occurredAt);
    }
}
