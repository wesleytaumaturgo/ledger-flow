package com.wesleytaumaturgo.ledgerflow.query.application.projectors;

import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.AccountCreated;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyDeposited;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyWithdrawn;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.TransferCompleted;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.TransferDirection;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.EventStoreRepository;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.BalanceView;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.AccountSummaryData;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.AccountSummaryRepository;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.TransactionHistoryRepository;
import com.wesleytaumaturgo.ledgerflow.shared.infrastructure.ProjectorFailedEventTracker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AccountProjector — no Spring context, no DB.
 * Verifies idempotency, state transitions, failure handling, and rebuild.
 */
@ExtendWith(MockitoExtension.class)
class AccountProjectorTest {

    @Mock
    private AccountSummaryRepository summaryRepository;

    @Mock
    private TransactionHistoryRepository historyRepository;

    @Mock
    private EventStoreRepository eventStoreRepository;

    private ProjectorFailedEventTracker failedEventTracker;
    private AccountProjector projector;

    @BeforeEach
    void setUp() {
        failedEventTracker = new ProjectorFailedEventTracker();
        projector = new AccountProjector(
            summaryRepository,
            historyRepository,
            failedEventTracker,
            new SimpleMeterRegistry(),
            eventStoreRepository
        );
    }

    // ── AccountCreated ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("on_accountCreated_createsAccountSummary: save called with balance=0, sequence=1")
    void on_accountCreated_createsAccountSummary() {
        UUID accountId = UUID.randomUUID();
        AccountCreated event = new AccountCreated(
            UUID.randomUUID(), accountId, "owner-1", Instant.now(), 1L);

        when(summaryRepository.findById(accountId)).thenReturn(Optional.empty());

        projector.on(event);

        ArgumentCaptor<AccountSummaryData> captor = ArgumentCaptor.forClass(AccountSummaryData.class);
        verify(summaryRepository).save(captor.capture());
        AccountSummaryData saved = captor.getValue();
        assertThat(saved.accountId()).isEqualTo(accountId);
        assertThat(saved.ownerId()).isEqualTo("owner-1");
        assertThat(saved.currentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.totalDeposited()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.totalWithdrawn()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.transactionCount()).isZero();
        assertThat(saved.lastEventSequence()).isEqualTo(1L);
    }

    // ── MoneyDeposited ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("on_moneyDeposited_updatesBalanceAndHistory: balance incremented, history saved")
    void on_moneyDeposited_updatesBalanceAndHistory() {
        UUID accountId = UUID.randomUUID();
        BalanceView existing = existingBalance(accountId, "100.00", 0, 1L, "0.00", "0.00");
        MoneyDeposited event = new MoneyDeposited(
            UUID.randomUUID(), accountId, new BigDecimal("50.00"), "BRL", Instant.now(), 2L);

        when(summaryRepository.findById(accountId)).thenReturn(Optional.of(existing));

        projector.on(event);

        ArgumentCaptor<AccountSummaryData> captor = ArgumentCaptor.forClass(AccountSummaryData.class);
        verify(summaryRepository).save(captor.capture());
        AccountSummaryData saved = captor.getValue();
        assertThat(saved.currentBalance()).isEqualByComparingTo("150.00");
        assertThat(saved.totalDeposited()).isEqualByComparingTo("50.00");
        assertThat(saved.transactionCount()).isEqualTo(1);
        assertThat(saved.lastEventSequence()).isEqualTo(2L);

        verify(historyRepository).save(any());
    }

    // ── MoneyWithdrawn ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("on_moneyWithdrawn_updatesBalanceAndHistory: balance decremented, history saved")
    void on_moneyWithdrawn_updatesBalanceAndHistory() {
        UUID accountId = UUID.randomUUID();
        BalanceView existing = existingBalance(accountId, "200.00", 1, 2L, "200.00", "0.00");
        MoneyWithdrawn event = new MoneyWithdrawn(
            UUID.randomUUID(), accountId, new BigDecimal("75.00"), "BRL", Instant.now(), 3L);

        when(summaryRepository.findById(accountId)).thenReturn(Optional.of(existing));

        projector.on(event);

        ArgumentCaptor<AccountSummaryData> captor = ArgumentCaptor.forClass(AccountSummaryData.class);
        verify(summaryRepository).save(captor.capture());
        AccountSummaryData saved = captor.getValue();
        assertThat(saved.currentBalance()).isEqualByComparingTo("125.00");
        assertThat(saved.totalWithdrawn()).isEqualByComparingTo("75.00");
        assertThat(saved.transactionCount()).isEqualTo(2);
        assertThat(saved.lastEventSequence()).isEqualTo(3L);

        verify(historyRepository).save(any());
    }

    // ── TransferCompleted DEBIT ─────────────────────────────────────────────────

    @Test
    @DisplayName("on_transferCompleted_debit_decreasesBalance: DEBIT subtracts from balance")
    void on_transferCompleted_debit_decreasesBalance() {
        UUID accountId = UUID.randomUUID();
        UUID counterpart = UUID.randomUUID();
        BalanceView existing = existingBalance(accountId, "300.00", 2, 3L, "300.00", "0.00");
        TransferCompleted event = new TransferCompleted(
            UUID.randomUUID(), accountId, counterpart,
            new BigDecimal("100.00"), "BRL", TransferDirection.DEBIT, Instant.now(), 4L);

        when(summaryRepository.findById(accountId)).thenReturn(Optional.of(existing));

        projector.on(event);

        ArgumentCaptor<AccountSummaryData> captor = ArgumentCaptor.forClass(AccountSummaryData.class);
        verify(summaryRepository).save(captor.capture());
        AccountSummaryData saved = captor.getValue();
        assertThat(saved.currentBalance()).isEqualByComparingTo("200.00");
        assertThat(saved.totalWithdrawn()).isEqualByComparingTo("100.00");
        assertThat(saved.transactionCount()).isEqualTo(3);
        assertThat(saved.lastEventSequence()).isEqualTo(4L);

        verify(historyRepository).save(any());
    }

    // ── TransferCompleted CREDIT ────────────────────────────────────────────────

    @Test
    @DisplayName("on_transferCompleted_credit_increasesBalance: CREDIT adds to balance")
    void on_transferCompleted_credit_increasesBalance() {
        UUID accountId = UUID.randomUUID();
        UUID counterpart = UUID.randomUUID();
        BalanceView existing = existingBalance(accountId, "100.00", 1, 2L, "100.00", "0.00");
        TransferCompleted event = new TransferCompleted(
            UUID.randomUUID(), accountId, counterpart,
            new BigDecimal("50.00"), "BRL", TransferDirection.CREDIT, Instant.now(), 3L);

        when(summaryRepository.findById(accountId)).thenReturn(Optional.of(existing));

        projector.on(event);

        ArgumentCaptor<AccountSummaryData> captor = ArgumentCaptor.forClass(AccountSummaryData.class);
        verify(summaryRepository).save(captor.capture());
        AccountSummaryData saved = captor.getValue();
        assertThat(saved.currentBalance()).isEqualByComparingTo("150.00");
        // Existing totalDeposited=100.00 + credit of 50.00 = 150.00
        assertThat(saved.totalDeposited()).isEqualByComparingTo("150.00");
        assertThat(saved.transactionCount()).isEqualTo(2);
        assertThat(saved.lastEventSequence()).isEqualTo(3L);

        verify(historyRepository).save(any());
    }

    // ── Idempotency ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("on_idempotency_sameEventTwice_noDoubleUpdate: skip if seq already processed")
    void on_idempotency_sameEventTwice_noDoubleUpdate() {
        UUID accountId = UUID.randomUUID();
        // Existing summary already has sequence=2 processed
        BalanceView existing = existingBalance(accountId, "150.00", 1, 2L, "150.00", "0.00");
        MoneyDeposited event = new MoneyDeposited(
            UUID.randomUUID(), accountId, new BigDecimal("50.00"), "BRL", Instant.now(), 2L);

        when(summaryRepository.findById(accountId)).thenReturn(Optional.of(existing));

        projector.on(event);

        // Save must NOT be called — event was already processed
        verify(summaryRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    // ── Exception handling ──────────────────────────────────────────────────────

    @Test
    @DisplayName("on_exception_doesNotThrow_tracksFailure: exceptions caught and tracked")
    void on_exception_doesNotThrow_tracksFailure() {
        UUID accountId = UUID.randomUUID();
        AccountCreated event = new AccountCreated(
            UUID.randomUUID(), accountId, "owner-1", Instant.now(), 1L);

        when(summaryRepository.findById(accountId)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new RuntimeException("DB error"))
            .when(summaryRepository).save(any());

        // Must NOT throw — projector catches all exceptions
        assertThatNoException().isThrownBy(() -> projector.on(event));

        // Failure must be tracked
        assertThat(failedEventTracker.size()).isEqualTo(1);
    }

    // ── Rebuild ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rebuild_deletesAndReplaysEvents: deleteById called, events replayed")
    void rebuild_deletesAndReplaysEvents() {
        UUID accountId = UUID.randomUUID();
        AccountCreated createdEvent = new AccountCreated(
            UUID.randomUUID(), accountId, "owner-1", Instant.now(), 1L);
        MoneyDeposited depositEvent = new MoneyDeposited(
            UUID.randomUUID(), accountId, new BigDecimal("100.00"), "BRL", Instant.now(), 2L);

        when(eventStoreRepository.load(accountId))
            .thenReturn(List.of(createdEvent, depositEvent));

        // After delete, findById returns empty (fresh state)
        when(summaryRepository.findById(accountId))
            .thenReturn(Optional.empty());

        RebuildResult result = projector.rebuild(accountId, eventStoreRepository);

        // Verify delete was called
        verify(summaryRepository).deleteById(accountId);
        verify(historyRepository).deleteByAccountId(accountId);

        // Verify result
        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.rebuiltEvents()).isEqualTo(2);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private BalanceView existingBalance(UUID accountId, String balance, int txCount,
                                        long lastSeq, String totalDeposited,
                                        String totalWithdrawn) {
        return new BalanceView(
            accountId,
            "owner-test",
            new BigDecimal(balance),
            "BRL",
            txCount,
            lastSeq,
            new BigDecimal(totalDeposited),
            new BigDecimal(totalWithdrawn),
            null
        );
    }
}
