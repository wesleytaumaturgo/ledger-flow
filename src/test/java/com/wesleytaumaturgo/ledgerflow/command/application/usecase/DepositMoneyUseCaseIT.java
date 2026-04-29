package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import com.wesleytaumaturgo.ledgerflow.IntegrationTestBase;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyResponse;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.AccountCreated;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyDeposited;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.EventStoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DepositMoneyUseCaseIT extends IntegrationTestBase {

    @Autowired
    private EventStoreRepository eventStoreRepository;

    @Test
    @DisplayName("POST /deposit persists MoneyDeposited with sequence_number=2 after AccountCreated")
    void post_deposit_persistsMoneyDeposited() {
        // Create account
        ResponseEntity<CreateAccountResponse> createResp = restTemplate.postForEntity(
            "/api/v1/accounts", new CreateAccountRequest("owner-deposit-it"),
            CreateAccountResponse.class);
        UUID accountId = createResp.getBody().accountId();

        // Deposit
        DepositMoneyRequest depositReq = new DepositMoneyRequest(new BigDecimal("250.00"), "BRL");
        ResponseEntity<DepositMoneyResponse> depositResp = restTemplate.postForEntity(
            "/api/v1/accounts/" + accountId + "/deposit",
            depositReq, DepositMoneyResponse.class);

        assertThat(depositResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(depositResp.getBody().balance()).isEqualByComparingTo("250.00");

        List<DomainEvent> events = eventStoreRepository.load(accountId);
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(AccountCreated.class);
        assertThat(events.get(1)).isInstanceOf(MoneyDeposited.class);
        MoneyDeposited deposited = (MoneyDeposited) events.get(1);
        assertThat(deposited.sequenceNumber()).isEqualTo(2L);
        assertThat(deposited.amount()).isEqualByComparingTo("250.00");
    }
}
