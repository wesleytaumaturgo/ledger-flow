package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import com.wesleytaumaturgo.ledgerflow.IntegrationTestBase;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountResponse;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.AccountCreated;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.EventStoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateAccountUseCaseIT extends IntegrationTestBase {

    @Autowired
    private EventStoreRepository eventStoreRepository;

    @Test
    @DisplayName("POST /api/v1/accounts persists AccountCreated row with sequence_number=1")
    void post_createAccount_persistsAccountCreatedEvent() {
        CreateAccountRequest request = new CreateAccountRequest("owner-it-1");

        ResponseEntity<CreateAccountResponse> response = restTemplate.postForEntity(
            "/api/v1/accounts", request, CreateAccountResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accountId()).isNotNull();
        assertThat(response.getHeaders().getLocation()).isNotNull();

        List<DomainEvent> events = eventStoreRepository.load(response.getBody().accountId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AccountCreated.class);
        AccountCreated created = (AccountCreated) events.get(0);
        assertThat(created.sequenceNumber()).isEqualTo(1L);
        assertThat(created.ownerId()).isEqualTo("owner-it-1");
    }
}
