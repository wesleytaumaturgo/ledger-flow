package com.wesleytaumaturgo.ledgerflow.api;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.query.application.projectors.AccountProjector;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.EventHistoryView;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.GetEventHistoryUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice test for the GET /api/v1/admin/accounts/{id}/events endpoint.
 *
 * AdminAuthFilter is auto-included in the slice. @TestPropertySource provides ADMIN_API_KEY.
 *
 * Covers:
 *   - 401 when X-Admin-Key header is absent
 *   - 404 ACCOUNT_NOT_FOUND when use case throws AccountNotFoundException
 *   - 200 with correct EventHistoryResponse shape including eventData field
 *
 * GET /api/v1/admin/accounts/{id}/events — REQ-event-history-audit / FR-013
 */
@WebMvcTest(AdminController.class)
@TestPropertySource(properties = "ADMIN_API_KEY=test-admin-secret")
class AdminEventHistoryControllerTest {

    private static final String VALID_KEY = "test-admin-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountProjector accountProjector;

    @MockBean
    private GetEventHistoryUseCase getEventHistoryUseCase;

    // ── 401 — missing X-Admin-Key ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /events without X-Admin-Key returns 401 ADMIN_AUTH_REQUIRED")
    void getEventHistory_noKey_returns401() throws Exception {
        UUID accountId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/admin/accounts/{id}/events", accountId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_AUTH_REQUIRED"));
    }

    // ── 404 — account not found ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /events with valid key but unknown account returns 404 ACCOUNT_NOT_FOUND")
    void getEventHistory_accountNotFound_returns404() throws Exception {
        UUID accountId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(getEventHistoryUseCase.execute(accountId))
                .thenThrow(new AccountNotFoundException(accountId));

        mockMvc.perform(get("/api/v1/admin/accounts/{id}/events", accountId)
                        .header("X-Admin-Key", VALID_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
    }

    // ── 200 — valid key, events returned ───────────────────────────────────────

    @Test
    @DisplayName("GET /events with valid key returns 200 with accountId, events list, and eventData")
    void getEventHistory_correctKey_returns200WithEvents() throws Exception {
        UUID accountId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID eventId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        when(getEventHistoryUseCase.execute(accountId))
                .thenReturn(List.of(new EventHistoryView(
                        eventId, "AccountCreated", "{\"ownerId\":\"test\"}", "{}", 1L, now)));

        mockMvc.perform(get("/api/v1/admin/accounts/{id}/events", accountId)
                        .header("X-Admin-Key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.events[0].eventType").value("AccountCreated"))
                .andExpect(jsonPath("$.events[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$.events[0].eventData").exists())
                .andExpect(jsonPath("$.events[0].eventData.ownerId").value("test"));
    }
}
