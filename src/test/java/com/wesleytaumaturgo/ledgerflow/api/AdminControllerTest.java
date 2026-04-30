package com.wesleytaumaturgo.ledgerflow.api;

import com.wesleytaumaturgo.ledgerflow.query.application.projectors.AccountProjector;
import com.wesleytaumaturgo.ledgerflow.query.application.projectors.RebuildResult;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.GetEventHistoryUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice test for AdminController.
 *
 * AdminAuthFilter is a @Component and is auto-included in the WebMvc test slice.
 * @TestPropertySource provides ADMIN_API_KEY so the filter can initialize.
 *
 * Covers:
 *   - 401 when X-Admin-Key header is absent (filter blocks request)
 *   - 401 when X-Admin-Key header has wrong value (filter blocks request)
 *   - 200 with correct response shape when X-Admin-Key is valid
 *   - 200 with rebuiltEvents=0 when account has no events
 *
 * POST /api/v1/admin/accounts/{id}/rebuild — REQ-rebuild-read-model / FR-015
 */
@WebMvcTest(AdminController.class)
@TestPropertySource(properties = "ADMIN_API_KEY=test-admin-secret")
class AdminControllerTest {

    private static final String VALID_KEY = "test-admin-secret";
    private static final String WRONG_KEY = "wrong-key";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountProjector accountProjector;

    @MockBean
    private GetEventHistoryUseCase getEventHistoryUseCase;

    // ── 401 — missing X-Admin-Key ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /rebuild without X-Admin-Key returns 401 ADMIN_AUTH_REQUIRED")
    void rebuild_noKey_returns401() throws Exception {
        UUID accountId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/rebuild", accountId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("ADMIN_AUTH_REQUIRED"));
    }

    // ── 401 — wrong X-Admin-Key ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /rebuild with wrong X-Admin-Key returns 401 ADMIN_AUTH_REQUIRED")
    void rebuild_wrongKey_returns401() throws Exception {
        UUID accountId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/rebuild", accountId)
                .header("X-Admin-Key", WRONG_KEY))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("ADMIN_AUTH_REQUIRED"));
    }

    // ── 200 — correct key, events replayed ─────────────────────────────────────

    @Test
    @DisplayName("POST /rebuild with correct X-Admin-Key returns 200 with accountId and rebuiltEvents")
    void rebuild_correctKey_returns200WithResult() throws Exception {
        UUID accountId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(accountProjector.rebuild(accountId))
            .thenReturn(new RebuildResult(accountId, 5));

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/rebuild", accountId)
                .header("X-Admin-Key", VALID_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId.toString()))
            .andExpect(jsonPath("$.rebuiltEvents").value(5));
    }

    // ── 200 — correct key, zero events ─────────────────────────────────────────

    @Test
    @DisplayName("POST /rebuild with correct key and no events returns 200 with rebuiltEvents=0")
    void rebuild_correctKey_noEvents_returns200WithZero() throws Exception {
        UUID accountId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(accountProjector.rebuild(accountId))
            .thenReturn(new RebuildResult(accountId, 0));

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/rebuild", accountId)
                .header("X-Admin-Key", VALID_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId.toString()))
            .andExpect(jsonPath("$.rebuiltEvents").value(0));
    }
}
