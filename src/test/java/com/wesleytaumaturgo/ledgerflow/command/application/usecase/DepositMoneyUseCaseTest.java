package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.InvalidAmountException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.OptimisticLockException;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.AccountCreated;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.EventStoreRepository;
import com.wesleytaumaturgo.ledgerflow.command.infrastructure.config.CommandProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepositMoneyUseCaseTest {

    private EventStoreRepository eventStore;
    private MeterRegistry meterRegistry;
    private DepositMoneyUseCase useCase;
    private static final String BRL = "BRL";

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStoreRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        CommandProperties properties = new CommandProperties(3);

        DepositMoneyUseCase realUseCase = new DepositMoneyUseCase(eventStore, properties, meterRegistry);
        useCase = new DepositMoneyUseCase(eventStore, properties, meterRegistry, realUseCase);
    }

    @Test
    @DisplayName("execute happy path: load -> reconstitute -> deposit -> save -> return DepositMoneyResult")
    void execute_happyPath_returnsResult_andIncrementsCounter() {
        UUID accountId = UUID.randomUUID();
        DepositMoneyCommand cmd = new DepositMoneyCommand(accountId, new BigDecimal("100.00"), BRL);

        AccountCreated created = new AccountCreated(
            UUID.randomUUID(), accountId, "owner-1",
            Instant.parse("2026-01-01T10:00:00Z"), 1L);
        when(eventStore.load(accountId)).thenReturn(List.of(created));

        DepositMoneyResult result = useCase.execute(cmd);

        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.balance()).isEqualByComparingTo("100.00");
        assertThat(result.currency()).isEqualTo(BRL);
        verify(eventStore).save(eq(accountId), eq("Account"), any(List.class));
        assertThat(meterRegistry.counter("account.command.deposit.total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("execute on missing aggregate throws AccountNotFoundException without retry")
    void execute_aggregateMissing_throwsAccountNotFound_noRetry() {
        UUID accountId = UUID.randomUUID();
        DepositMoneyCommand cmd = new DepositMoneyCommand(accountId, new BigDecimal("100.00"), BRL);
        when(eventStore.load(accountId)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(cmd))
            .isInstanceOf(AccountNotFoundException.class);

        verify(eventStore, times(1)).load(accountId);
        assertThat(meterRegistry.counter("account.command.retry.total", "operation", "deposit").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("execute retries on OptimisticLockException and increments retry counter")
    void execute_optimisticLockException_retriesAndSucceeds() {
        UUID accountId = UUID.randomUUID();
        DepositMoneyCommand cmd = new DepositMoneyCommand(accountId, new BigDecimal("100.00"), BRL);
        AccountCreated created = new AccountCreated(
            UUID.randomUUID(), accountId, "owner-1",
            Instant.parse("2026-01-01T10:00:00Z"), 1L);
        when(eventStore.load(accountId)).thenReturn(List.of(created));

        doThrow(new OptimisticLockException(accountId)).doNothing()
            .when(eventStore).save(any(UUID.class), anyString(), any(List.class));

        DepositMoneyResult result = useCase.execute(cmd);

        assertThat(result).isNotNull();
        verify(eventStore, times(2)).save(any(UUID.class), anyString(), any(List.class));
        assertThat(meterRegistry.counter("account.command.retry.total", "operation", "deposit").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("account.command.optimistic_lock_exhausted.total", "operation", "deposit").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("execute exhausts retries and rethrows OptimisticLockException, incrementing exhausted counter")
    void execute_exhaustedRetries_rethrowsAndIncrementsExhaustedCounter() {
        UUID accountId = UUID.randomUUID();
        DepositMoneyCommand cmd = new DepositMoneyCommand(accountId, new BigDecimal("100.00"), BRL);
        AccountCreated created = new AccountCreated(
            UUID.randomUUID(), accountId, "owner-1",
            Instant.parse("2026-01-01T10:00:00Z"), 1L);
        when(eventStore.load(accountId)).thenReturn(List.of(created));
        doThrow(new OptimisticLockException(accountId))
            .when(eventStore).save(any(UUID.class), anyString(), any(List.class));

        assertThatThrownBy(() -> useCase.execute(cmd))
            .isInstanceOf(OptimisticLockException.class);

        verify(eventStore, times(3)).save(any(UUID.class), anyString(), any(List.class));
        assertThat(meterRegistry.counter("account.command.retry.total", "operation", "deposit").count()).isEqualTo(2.0);
        assertThat(meterRegistry.counter("account.command.optimistic_lock_exhausted.total", "operation", "deposit").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("execute with zero amount propagates InvalidAmountException without retry")
    void execute_zeroAmount_throwsInvalidAmount() {
        UUID accountId = UUID.randomUUID();
        DepositMoneyCommand cmd = new DepositMoneyCommand(accountId, BigDecimal.ZERO, BRL);
        AccountCreated created = new AccountCreated(
            UUID.randomUUID(), accountId, "owner-1",
            Instant.parse("2026-01-01T10:00:00Z"), 1L);
        when(eventStore.load(accountId)).thenReturn(List.of(created));

        assertThatThrownBy(() -> useCase.execute(cmd))
            .isInstanceOf(InvalidAmountException.class);
    }
}
