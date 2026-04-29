package com.wesleytaumaturgo.ledgerflow.query.application.usecase;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.AccountSummaryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GetBalanceUseCase.
 * Verifies: success path, not-found path, and Timer metric tags.
 */
@ExtendWith(MockitoExtension.class)
class GetBalanceUseCaseTest {

    @Mock
    private AccountSummaryRepository repository;

    private MeterRegistry meterRegistry;
    private GetBalanceUseCase useCase;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        useCase = new GetBalanceUseCase(repository, meterRegistry);
    }

    @Test
    @DisplayName("execute_existingAccount_returnsBalanceView")
    void execute_existingAccount_returnsBalanceView() {
        UUID accountId = UUID.randomUUID();
        BalanceView expectedView = new BalanceView(
                accountId,
                "owner-123",
                new BigDecimal("500.00"),
                "BRL",
                3,
                5L,
                new BigDecimal("600.00"),
                new BigDecimal("100.00"),
                Instant.now()
        );

        when(repository.findById(accountId)).thenReturn(Optional.of(expectedView));

        BalanceView result = useCase.execute(accountId);

        assertThat(result).isEqualTo(expectedView);
        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.ownerId()).isEqualTo("owner-123");
        assertThat(result.balance()).isEqualByComparingTo("500.00");
        assertThat(result.currency()).isEqualTo("BRL");
        assertThat(result.transactionCount()).isEqualTo(3);
        assertThat(result.lastEventSequence()).isEqualTo(5L);
        assertThat(result.totalDeposited()).isEqualByComparingTo("600.00");
        assertThat(result.totalWithdrawn()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("execute_missingAccount_throwsAccountNotFoundException")
    void execute_missingAccount_throwsAccountNotFoundException() {
        UUID accountId = UUID.randomUUID();

        when(repository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(accountId))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("execute_success_recordsTimerWithOutcomeSuccess")
    void execute_success_recordsTimerWithOutcomeSuccess() {
        UUID accountId = UUID.randomUUID();
        BalanceView view = new BalanceView(
                accountId, "owner-1", BigDecimal.TEN, "BRL", 1, 1L,
                BigDecimal.TEN, BigDecimal.ZERO, Instant.now()
        );

        when(repository.findById(accountId)).thenReturn(Optional.of(view));

        useCase.execute(accountId);

        Timer successTimer = meterRegistry.find("query.balance.duration")
                .tag("outcome", "success")
                .timer();
        assertThat(successTimer).isNotNull();
        assertThat(successTimer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("execute_notFound_recordsTimerWithOutcomeNotFound")
    void execute_notFound_recordsTimerWithOutcomeNotFound() {
        UUID accountId = UUID.randomUUID();

        when(repository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(accountId))
                .isInstanceOf(AccountNotFoundException.class);

        Timer notFoundTimer = meterRegistry.find("query.balance.duration")
                .tag("outcome", "not_found")
                .timer();
        assertThat(notFoundTimer).isNotNull();
        assertThat(notFoundTimer.count()).isEqualTo(1);
    }
}
