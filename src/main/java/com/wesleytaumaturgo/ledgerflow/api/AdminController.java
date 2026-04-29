package com.wesleytaumaturgo.ledgerflow.api;

import com.wesleytaumaturgo.ledgerflow.query.application.projectors.AccountProjector;
import com.wesleytaumaturgo.ledgerflow.query.application.projectors.RebuildResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin REST controller — protected by AdminAuthFilter (X-Admin-Key header).
 *
 * Rules (CLAUDE.md §3.1, controllers.md):
 *   - Zero business logic — delegates entirely to AccountProjector
 *   - No @ExceptionHandler — all exceptions handled by GlobalExceptionHandler
 *   - No command.domain imports — api layer must not depend on command/domain (ArchUnit Rule 5)
 *   - Authentication handled upstream by AdminAuthFilter before reaching this controller
 *
 * POST /api/v1/admin/accounts/{id}/rebuild — REQ-rebuild-read-model / FR-015
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AccountProjector accountProjector;

    public AdminController(AccountProjector accountProjector) {
        this.accountProjector = accountProjector;
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
}
