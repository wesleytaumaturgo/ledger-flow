package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.InsufficientFundsException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.SelfTransferNotAllowedException;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferMoneyUseCaseTest {

    private EventStoreRepository eventStore;
    private MeterRegistry meterRegistry;
    private TransferMoneyUseCase useCase;
    private static final String BRL = "BRL";

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStoreRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        CommandProperties properties = new CommandProperties(3);
        TransferMoneyUseCase real = new TransferMoneyUseCase(eventStore, properties, meterRegistry);
        useCase = new TransferMoneyUseCase(eventStore, properties, meterRegistry, real);
    }

    @Test
    @DisplayName("execute happy path: validate + load both + reconstitute + debit + credit + save both")
    void execute_happyPath_savesBothAggregates() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        TransferMoneyCommand cmd = new TransferMoneyCommand(source, target, new BigDecimal("30.00"), BRL);

        when(eventStore.load(source)).thenReturn(List.of(
            new AccountCreated(UUID.randomUUID(), source, "owner-A",
                Instant.parse("2026-01-01T10:00:00Z"), 1L),
            new MoneyDeposited(UUID.randomUUID(), source, new BigDecimal("100.00"), BRL,
                Instant.parse("2026-01-01T10:01:00Z"), 2L)
        ));
        when(eventStore.load(target)).thenReturn(List.of(
            new AccountCreated(UUID.randomUUID(), target, "owner-B",
                Instant.parse("2026-01-01T10:00:00Z"), 1L)
        ));

        TransferMoneyResult result = useCase.execute(cmd);

        assertThat(result.sourceAccountId()).isEqualTo(source);
        assertThat(result.targetAccountId()).isEqualTo(target);
        verify(eventStore).save(eq(source), eq("Account"), any(List.class));
        verify(eventStore).save(eq(target), eq("Account"), any(List.class));
        assertThat(meterRegistry.counter("account.command.transfer.total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("execute self-transfer (source == target) throws SelfTransferNotAllowedException BEFORE any DB I/O")
    void execute_selfTransfer_throwsBeforeDbCall() {
        UUID id = UUID.randomUUID();
        TransferMoneyCommand cmd = new TransferMoneyCommand(id, id, new BigDecimal("10.00"), BRL);

        assertThatThrownBy(() -> useCase.execute(cmd))
            .isInstanceOf(SelfTransferNotAllowedException.class);

        verify(eventStore, never()).load(any(UUID.class));
    }

    @Test
    @DisplayName("execute on missing source aggregate throws AccountNotFoundException(source)")
    void execute_sourceMissing_throwsAccountNotFound() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        TransferMoneyCommand cmd = new TransferMoneyCommand(source, target, new BigDecimal("10.00"), BRL);
        when(eventStore.load(source)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(cmd))
            .isInstanceOf(AccountNotFoundException.class)
            .hasMessageContaining(source.toString());

        verify(eventStore, never()).load(target);
    }

    @Test
    @DisplayName("execute on missing target aggregate throws AccountNotFoundException(target)")
    void execute_targetMissing_throwsAccountNotFound() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        TransferMoneyCommand cmd = new TransferMoneyCommand(source, target, new BigDecimal("10.00"), BRL);
        when(eventStore.load(source)).thenReturn(List.of(
            new AccountCreated(UUID.randomUUID(), source, "owner-A",
                Instant.parse("2026-01-01T10:00:00Z"), 1L)
        ));
        when(eventStore.load(target)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(cmd))
            .isInstanceOf(AccountNotFoundException.class)
            .hasMessageContaining(target.toString());
    }

    @Test
    @DisplayName("execute on insufficient source balance throws InsufficientFundsException")
    void execute_insufficientBalance_throws() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        TransferMoneyCommand cmd = new TransferMoneyCommand(source, target, new BigDecimal("500.00"), BRL);
        when(eventStore.load(source)).thenReturn(List.of(
            new AccountCreated(UUID.randomUUID(), source, "owner-A",
                Instant.parse("2026-01-01T10:00:00Z"), 1L)
        ));
        when(eventStore.load(target)).thenReturn(List.of(
            new AccountCreated(UUID.randomUUID(), target, "owner-B",
                Instant.parse("2026-01-01T10:00:00Z"), 1L)
        ));

        assertThatThrownBy(() -> useCase.execute(cmd))
            .isInstanceOf(InsufficientFundsException.class);

        verify(eventStore, never()).save(any(UUID.class), anyString(), any(List.class));
    }
}
