package com.wesleytaumaturgo.ledgerflow.api;

import com.wesleytaumaturgo.ledgerflow.api.dto.EventHistoryResponse;
import com.wesleytaumaturgo.ledgerflow.query.application.projectors.AccountProjector;
import com.wesleytaumaturgo.ledgerflow.query.application.projectors.RebuildResult;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.EventHistoryView;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.GetEventHistoryUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin REST controller — protected by AdminAuthFilter (X-Admin-Key header).
 *
 * Rules (CLAUDE.md §3.1, controllers.md):
 *   - Zero business logic — delegates to use cases and projectors
 *   - No @ExceptionHandler — all exceptions handled by GlobalExceptionHandler
 *   - No command.domain imports — api layer must not depend on command/domain (ArchUnit Rule 5)
 *   - Authentication handled upstream by AdminAuthFilter before reaching this controller
 *
 * POST /api/v1/admin/accounts/{id}/rebuild — REQ-rebuild-read-model / FR-015
 * GET  /api/v1/admin/accounts/{id}/events  — REQ-event-history-audit / FR-013
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AccountProjector accountProjector;
    private final GetEventHistoryUseCase getEventHistoryUseCase;

    public AdminController(AccountProjector accountProjector,
                           GetEventHistoryUseCase getEventHistoryUseCase) {
        this.accountProjector = accountProjector;
        this.getEventHistoryUseCase = getEventHistoryUseCase;
    }

    /**
     * Rebuilds the read model for the given account from the event store.
     *
     * Deletes existing account_summary and transaction_history rows for the account,
     * then replays all events in sequence order. Returns the number of replayed events.
     *
     * Account with no events returns 200 with rebuiltEvents=0 (not 404).
     * Authentication is enforced by AdminAuthFilter before this method is called.
     * EventStoreRepository is injected into AccountProjector directly — not exposed to api layer.
     *
     * @param id the account ID to rebuild
     * @return 200 OK with {accountId, rebuiltEvents}
     */
    @PostMapping("/accounts/{id}/rebuild")
    public ResponseEntity<RebuildResult> rebuild(@PathVariable UUID id) {
        RebuildResult result = accountProjector.rebuild(id);
        return ResponseEntity.ok(result);
    }

    /**
     * Returns all raw events for an account from the event store, ordered by sequenceNumber ASC.
     *
     * Reads directly from event_store via EventStoreRepository — bypasses read models.
     * Account with no events returns 404 ACCOUNT_NOT_FOUND.
     * Authentication is enforced by AdminAuthFilter before this method is called.
     *
     * @param id the account ID to query
     * @return 200 OK with {accountId, events[]}
     */
    @GetMapping("/accounts/{id}/events")
    public ResponseEntity<EventHistoryResponse> getEventHistory(@PathVariable UUID id) {
        List<EventHistoryView> views = getEventHistoryUseCase.execute(id);
        List<EventHistoryResponse.EventEntry> entries = views.stream()
                .map(v -> new EventHistoryResponse.EventEntry(
                        v.eventId(), v.eventType(), v.sequenceNumber(), v.occurredAt()))
                .toList();
        return ResponseEntity.ok(new EventHistoryResponse(id, entries));
    }
}
