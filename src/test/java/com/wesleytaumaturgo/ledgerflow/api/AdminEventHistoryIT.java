package com.wesleytaumaturgo.ledgerflow.api;

import com.wesleytaumaturgo.ledgerflow.IntegrationTestBase;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for GET /api/v1/admin/accounts/{id}/events.
 *
 * Tests the full event history flow against real PostgreSQL:
 *   1. Create account + 2 deposits via command side
 *   2. GET /api/v1/admin/accounts/{id}/events with correct X-Admin-Key
 *   3. Assert 200, 3 events (AccountCreated + 2x MoneyDeposited), ordered by sequenceNumber ASC
 *   4. Assert 404 ACCOUNT_NOT_FOUND for an unknown account UUID
 *
 * Auth enforcement (401) is tested in AdminEventHistoryControllerTest (@WebMvcTest slice).
 *
 * GET /api/v1/admin/accounts/{id}/events — REQ-event-history-audit / FR-013
 */
class AdminEventHistoryIT extends IntegrationTestBase {

    @Value("${ADMIN_API_KEY}")
    private String adminApiKey;

    // ── Full event history flow ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /events returns events in sequenceNumber ASC order")
    void getEventHistory_returnsEventsInSequenceOrder() {
        // Step 1: Create account
        ResponseEntity<CreateAccountResponse> createResp = restTemplate.postForEntity(
                "/api/v1/accounts",
                new CreateAccountRequest("owner-event-history-it"),
                CreateAccountResponse.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID accountId = createResp.getBody().accountId();

        // Step 2: Deposit twice
        restTemplate.postForEntity(
                "/api/v1/accounts/" + accountId + "/deposit",
                new DepositMoneyRequest(new BigDecimal("50.00"), "BRL"),
                Object.class);
        restTemplate.postForEntity(
                "/api/v1/accounts/" + accountId + "/deposit",
                new DepositMoneyRequest(new BigDecimal("30.00"), "BRL"),
                Object.class);

        // Step 3: GET event history with admin key
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Key", adminApiKey);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<EventHistoryBody> response = restTemplate.exchange(
                "/api/v1/admin/accounts/" + accountId + "/events",
                HttpMethod.GET,
                request,
                EventHistoryBody.class);

        // Step 4: Assert 200 with 3 events (AccountCreated + 2x MoneyDeposited)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        EventHistoryBody body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.accountId()).isEqualTo(accountId);
        assertThat(body.events()).hasSize(3);

        // Step 5: Assert events are in sequenceNumber ASC order
        List<EventEntry> events = body.events();
        assertThat(events.get(0).sequenceNumber()).isEqualTo(1L);
        assertThat(events.get(0).eventType()).isEqualTo("AccountCreated");

        assertThat(events.get(1).sequenceNumber()).isEqualTo(2L);
        assertThat(events.get(1).eventType()).isEqualTo("MoneyDeposited");

        assertThat(events.get(2).sequenceNumber()).isEqualTo(3L);
        assertThat(events.get(2).eventType()).isEqualTo("MoneyDeposited");

        // Step 6: Each event has non-null eventId and occurredAt
        events.forEach(e -> {
            assertThat(e.eventId()).isNotNull();
            assertThat(e.occurredAt()).isNotNull();
        });
    }

    // ── 404 for unknown account ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /events for unknown account returns 404 ACCOUNT_NOT_FOUND")
    @SuppressWarnings("unchecked")
    void getEventHistory_unknownAccount_returns404() {
        UUID unknownId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Key", adminApiKey);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/accounts/" + unknownId + "/events",
                HttpMethod.GET,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("errorCode", "ACCOUNT_NOT_FOUND");
    }

    // ── Response body helpers ───────────────────────────────────────────────────

    record EventHistoryBody(UUID accountId, List<EventEntry> events) {}

    record EventEntry(UUID eventId, String eventType, long sequenceNumber, String occurredAt) {}
}
