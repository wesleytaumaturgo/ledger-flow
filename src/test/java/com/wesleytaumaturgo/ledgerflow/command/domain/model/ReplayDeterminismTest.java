package com.wesleytaumaturgo.ledgerflow.command.domain.model;

import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.AccountCreated;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyDeposited;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyWithdrawn;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.TransferCompleted;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.TransferDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Replay determinism guarantee test (REQ-replay-determinism / FR-016,
 * REQ-nfr-replay-determinism-test / NFR-017).
 *
 * Replays a fixed 5-event list THREE times and asserts identical balance, version, and id
 * on every replay. This is the empirical proof that no apply*() method has non-deterministic
 * side effects (REQ-nfr-no-instant-in-apply / NFR-006). If any apply method called
 * Instant.now() the state would diverge across replays within a single JVM run.
 *
 * Pure unit test — no Spring context, no database. Runs in CI on every build via Surefire.
 * Closes FORGE §4.7 quality gate: "ReplayDeterminismTest exists and passes".
 */
class ReplayDeterminismTest {

    private static final String BRL = "BRL";

    // Deterministic fixtures — hardcoded UUIDs and parsed Instants so the test is
    // byte-for-byte reproducible across runs and machines (T-02-16 mitigation).
    private static final UUID ACCOUNT_ID  = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COUNTERPART = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EVENT_1     = UUID.fromString("aaaaaaaa-0001-0000-0000-000000000001");
    private static final UUID EVENT_2     = UUID.fromString("aaaaaaaa-0002-0000-0000-000000000002");
    private static final UUID EVENT_3     = UUID.fromString("aaaaaaaa-0003-0000-0000-000000000003");
    private static final UUID EVENT_4     = UUID.fromString("aaaaaaaa-0004-0000-0000-000000000004");
    private static final UUID EVENT_5     = UUID.fromString("aaaaaaaa-0005-0000-0000-000000000005");

    @Test
    @DisplayName("Replaying same 5-event list 3 times produces identical balance(), version(), and id()")
    void reconstitute_sameEventsThreeTimes_producesIdenticalState() {
        // Arrange — fixed 5-event history covering all four event types
        // Sequence: create → deposit 100 → deposit 50 → withdraw 30 → transfer debit 20
        // Expected final balance: 0 + 100 + 50 - 30 - 20 = 100.00
        List<DomainEvent> events = List.of(
            new AccountCreated(
                EVENT_1, ACCOUNT_ID, "owner-1",
                Instant.parse("2026-01-01T10:00:00Z"), 1L),
            new MoneyDeposited(
                EVENT_2, ACCOUNT_ID, new BigDecimal("100.00"), BRL,
                Instant.parse("2026-01-01T10:01:00Z"), 2L),
            new MoneyDeposited(
                EVENT_3, ACCOUNT_ID, new BigDecimal("50.00"), BRL,
                Instant.parse("2026-01-01T10:02:00Z"), 3L),
            new MoneyWithdrawn(
                EVENT_4, ACCOUNT_ID, new BigDecimal("30.00"), BRL,
                Instant.parse("2026-01-01T10:03:00Z"), 4L),
            new TransferCompleted(
                EVENT_5, ACCOUNT_ID, COUNTERPART,
                new BigDecimal("20.00"), BRL,
                TransferDirection.DEBIT,
                Instant.parse("2026-01-01T10:04:00Z"), 5L)
        );

        // Act — replay three independent times from the same immutable event list
        Account first  = Account.reconstitute(events);
        Account second = Account.reconstitute(events);
        Account third  = Account.reconstitute(events);

        // Assert — balance identical across all three replays
        assertThat(first.balance()).isEqualTo(second.balance());
        assertThat(second.balance()).isEqualTo(third.balance());
        assertThat(first.balance().amount()).isEqualByComparingTo("100.00");

        // Assert — version identical across all three replays
        assertThat(first.version()).isEqualTo(second.version());
        assertThat(second.version()).isEqualTo(third.version());
        assertThat(first.version()).isEqualTo(5L);

        // Assert — identity identical across all three replays
        assertThat(first.id()).isEqualTo(second.id());
        assertThat(second.id()).isEqualTo(third.id());
        assertThat(first.id().value()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    @DisplayName("reconstitute with empty list throws IllegalArgumentException containing 'empty'")
    void reconstitute_emptyList_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Account.reconstitute(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }
}
