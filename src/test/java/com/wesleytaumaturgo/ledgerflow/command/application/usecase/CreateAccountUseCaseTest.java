package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.AccountCreated;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.EventStoreRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CreateAccountUseCaseTest {

    private EventStoreRepository eventStore;
    private MeterRegistry meterRegistry;
    private CreateAccountUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStoreRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        useCase = new CreateAccountUseCase(eventStore, meterRegistry);
    }

    @Test
    @DisplayName("execute persists a single AccountCreated event and returns CreateAccountResult with non-null accountId")
    void execute_persistsAccountCreated_returnsAccountId() {
        CreateAccountCommand cmd = new CreateAccountCommand("owner-1");

        CreateAccountResult result = useCase.execute(cmd);

        assertThat(result).isNotNull();
        assertThat(result.accountId()).isNotNull();
        verify(eventStore).save(eq(result.accountId()), eq("Account"), any(List.class));
        assertThat(meterRegistry.counter("account.command.create.total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("execute increments commands_executed_total counter with command_type=CreateAccount on success")
    void execute_incrementsCommandsExecutedTotal() {
        CreateAccountCommand cmd = new CreateAccountCommand("owner-1");

        useCase.execute(cmd);

        assertThat(meterRegistry.counter("commands_executed_total", "command_type", "CreateAccount").count())
            .isEqualTo(1.0);
    }

    @Test
    @DisplayName("CreateAccountCommand with null ownerId throws NullPointerException at construction")
    void command_nullOwnerId_throws() {
        assertThatThrownBy(() -> new CreateAccountCommand(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("ownerId");
    }
}
