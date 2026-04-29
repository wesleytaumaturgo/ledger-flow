package com.wesleytaumaturgo.ledgerflow.query.application.usecase;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.AccountSummaryRepository;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.TransactionHistoryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GetTransactionHistoryUseCase.
 * Verifies: account-not-found guard, null filter passthrough,
 * event-type filter, empty page beyond total, and Timer metric tags.
 */
@ExtendWith(MockitoExtension.class)
class GetTransactionHistoryUseCaseTest {

    @Mock
    private TransactionHistoryRepository historyRepository;

    @Mock
    private AccountSummaryRepository summaryRepository;

    private MeterRegistry meterRegistry;
    private GetTransactionHistoryUseCase useCase;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        useCase = new GetTransactionHistoryUseCase(historyRepository, summaryRepository, meterRegistry);
    }

    @Test
    @DisplayName("execute_existingAccount_nullFilters_returnsPage")
    void execute_existingAccount_nullFilters_returnsPage() {
        UUID accountId = UUID.randomUUID();
        BalanceView summary = balanceView(accountId);
        TransactionHistoryView txView = txView(accountId);
        Pageable pageable = PageRequest.of(0, 10);
        TransactionFilter filter = TransactionFilter.noFilter();

        when(summaryRepository.findById(accountId)).thenReturn(Optional.of(summary));
        when(historyRepository.findByAccountId(accountId, filter, 0, 10))
                .thenReturn(List.of(txView));
        when(historyRepository.countByAccountId(accountId, filter)).thenReturn(1L);

        Page<TransactionHistoryView> result = useCase.execute(accountId, filter, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isEqualTo(txView);
        assertThat(result.getTotalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("execute_missingAccount_throwsAccountNotFoundException")
    void execute_missingAccount_throwsAccountNotFoundException() {
        UUID accountId = UUID.randomUUID();
        TransactionFilter filter = TransactionFilter.noFilter();
        Pageable pageable = PageRequest.of(0, 10);

        when(summaryRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(accountId, filter, pageable))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("execute_withEventTypeFilter_passesFilterToRepo")
    void execute_withEventTypeFilter_passesFilterToRepo() {
        UUID accountId = UUID.randomUUID();
        BalanceView summary = balanceView(accountId);
        TransactionFilter filter = new TransactionFilter(null, null, "DEPOSIT");
        Pageable pageable = PageRequest.of(0, 10);

        when(summaryRepository.findById(accountId)).thenReturn(Optional.of(summary));
        when(historyRepository.findByAccountId(eq(accountId), eq(filter), eq(0), eq(10)))
                .thenReturn(List.of());
        when(historyRepository.countByAccountId(eq(accountId), eq(filter))).thenReturn(0L);

        useCase.execute(accountId, filter, pageable);

        verify(historyRepository).findByAccountId(accountId, filter, 0, 10);
        verify(historyRepository).countByAccountId(accountId, filter);
    }

    @Test
    @DisplayName("execute_pageBeyondTotal_returnsEmptyPage")
    void execute_pageBeyondTotal_returnsEmptyPage() {
        UUID accountId = UUID.randomUUID();
        BalanceView summary = balanceView(accountId);
        TransactionFilter filter = TransactionFilter.noFilter();
        Pageable pageable = PageRequest.of(99, 10);

        when(summaryRepository.findById(accountId)).thenReturn(Optional.of(summary));
        when(historyRepository.findByAccountId(accountId, filter, 99, 10))
                .thenReturn(List.of());
        when(historyRepository.countByAccountId(accountId, filter)).thenReturn(5L);

        Page<TransactionHistoryView> result = useCase.execute(accountId, filter, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(5L);
    }

    @Test
    @DisplayName("execute_success_recordsTimerWithOutcomeSuccess")
    void execute_success_recordsTimerWithOutcomeSuccess() {
        UUID accountId = UUID.randomUUID();
        BalanceView summary = balanceView(accountId);
        TransactionFilter filter = TransactionFilter.noFilter();
        Pageable pageable = PageRequest.of(0, 10);

        when(summaryRepository.findById(accountId)).thenReturn(Optional.of(summary));
        when(historyRepository.findByAccountId(any(), any(), eq(0), eq(10))).thenReturn(List.of());
        when(historyRepository.countByAccountId(any(), any())).thenReturn(0L);

        useCase.execute(accountId, filter, pageable);

        Timer successTimer = meterRegistry.find("query.transactions.duration")
                .tag("outcome", "success")
                .timer();
        assertThat(successTimer).isNotNull();
        assertThat(successTimer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("execute_notFound_recordsTimerWithOutcomeNotFound")
    void execute_notFound_recordsTimerWithOutcomeNotFound() {
        UUID accountId = UUID.randomUUID();
        TransactionFilter filter = TransactionFilter.noFilter();
        Pageable pageable = PageRequest.of(0, 10);

        when(summaryRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(accountId, filter, pageable))
                .isInstanceOf(AccountNotFoundException.class);

        Timer notFoundTimer = meterRegistry.find("query.transactions.duration")
                .tag("outcome", "not_found")
                .timer();
        assertThat(notFoundTimer).isNotNull();
        assertThat(notFoundTimer.count()).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static BalanceView balanceView(UUID accountId) {
        return new BalanceView(
                accountId, "owner-1",
                BigDecimal.valueOf(1000, 2), "BRL",
                2, 2L,
                BigDecimal.valueOf(1000, 2), BigDecimal.ZERO,
                Instant.now());
    }

    private static TransactionHistoryView txView(UUID accountId) {
        return new TransactionHistoryView(
                UUID.randomUUID(), accountId,
                "DEPOSIT",
                BigDecimal.valueOf(10000, 2), "BRL",
                "Deposit", Instant.now(), null);
    }
}
