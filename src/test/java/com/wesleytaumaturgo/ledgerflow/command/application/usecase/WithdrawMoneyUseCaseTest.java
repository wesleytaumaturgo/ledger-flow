package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.InsufficientFundsException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.OptimisticLockException;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.AccountCreated;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyDeposited;
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

class WithdrawMoneyUseCaseTest {

    private EventStoreRepository eventStore;
    private MeterRegistry meterRegistry;
    private WithdrawMoneyUseCase useCase;
    private static final String BRL = "BRL";

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStoreRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        CommandProperties properties = new CommandProperties(3);
        WithdrawMoneyUseCase real = new WithdrawMoneyUseCase(eventStore, properties, meterRegistry);
        useCase = new WithdrawMoneyUseCase(eventStore, properties, meterRegistry, real);
    }

    @Test
    @DisplayName("execute happy path: load -> reconstitute -> withdraw -> save -> return result with reduced balance")
    void execute_happyPath_reducesBalance() {
        UUID accountId = UUID.randomUUID();
        WithdrawMoneyCommand cmd = new WithdrawMoneyCommand(accountId, new BigDecimal("30.00"), BRL);
        when(eventStore.load(accountId)).thenReturn(List.of(
            new AccountCreated(UUID.randomUUID(), accountId, "owner-1",
                Instant.parse("2026-01-01T10:00:00Z"), 1L),
            new MoneyDeposited(UUID.randomUUID(), accountId, new BigDecimal("100.00"), BRL,
                Instant.parse("2026-01-01T10:01:00Z"), 2L)
        ));

        WithdrawMoneyResult result = useCase.execute(cmd);

        assertThat(result.balance()).isEqualByComparingTo("70.00");
        verify(eventStore).save(eq(accountId), eq("Account"), any(List.class));
        assertThat(meterRegistry.counter("account.command.withdraw.total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("execute on overdraft throws InsufficientFundsException without retry")
    void execute_overdraft_throwsInsufficientFunds_noRetry() {
        UUID accountId = UUID.randomUUID();
        WithdrawMoneyCommand cmd = new WithdrawMoneyCommand(accountId, new BigDecimal("500.00"), BRL);
        when(eventStore.load(accountId)).thenReturn(List.of(
            new AccountCreated(UUID.randomUUID(), accountId, "owner-1",
                Instant.parse("2026-01-01T10:00:00Z"), 1L)
        ));

        assertThatThrownBy(() -> useCase.execute(cmd))
            .isInstanceOf(InsufficientFundsException.class);

        verify(eventStore, times(1)).load(accountId);
        assertThat(meterRegistry.counter("account.command.retry.total", "operation", "withdraw").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("execute on missing account throws AccountNotFoundException without retry")
    void execute_missingAccount_throwsAccountNotFound() {
        UUID accountId = UUID.randomUUID();
        WithdrawMoneyCommand cmd = new WithdrawMoneyCommand(accountId, new BigDecimal("10.00"), BRL);
        when(eventStore.load(accountId)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(cmd))
            .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("execute retries on OptimisticLockException up to maxRetries-1 times then succeeds")
    void execute_retries_onOptimisticLockException_succeeds() {
        UUID accountId = UUID.randomUUID();
        WithdrawMoneyCommand cmd = new WithdrawMoneyCommand(accountId, new BigDecimal("10.00"), BRL);
        when(eventStore.load(accountId)).thenReturn(List.of(
            new AccountCreated(UUID.randomUUID(), accountId, "owner-1",
                Instant.parse("2026-01-01T10:00:00Z"), 1L),
            new MoneyDeposited(UUID.randomUUID(), accountId, new BigDecimal("100.00"), BRL,
                Instant.parse("2026-01-01T10:01:00Z"), 2L)
        ));
        doThrow(new OptimisticLockException(accountId)).doNothing()
            .when(eventStore).save(any(UUID.class), anyString(), any(List.class));

        WithdrawMoneyResult result = useCase.execute(cmd);

        assertThat(result).isNotNull();
        verify(eventStore, times(2)).save(any(UUID.class), anyString(), any(List.class));
        assertThat(meterRegistry.counter("account.command.retry.total", "operation", "withdraw").count()).isEqualTo(1.0);
    }
}
