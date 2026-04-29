package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import com.wesleytaumaturgo.ledgerflow.IntegrationTestBase;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.WithdrawMoneyRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.WithdrawMoneyResponse;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyWithdrawn;
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

class WithdrawMoneyUseCaseIT extends IntegrationTestBase {

    @Autowired
    private EventStoreRepository eventStoreRepository;

    @Test
    @DisplayName("POST /withdraw persists MoneyWithdrawn with sequence_number=3 after create+deposit")
    void post_withdraw_persistsMoneyWithdrawn() {
        // Seed: create + deposit 100
        UUID accountId = restTemplate.postForObject(
            "/api/v1/accounts", new CreateAccountRequest("owner-withdraw-it"),
            CreateAccountResponse.class).accountId();
        restTemplate.postForObject(
            "/api/v1/accounts/" + accountId + "/deposit",
            new DepositMoneyRequest(new BigDecimal("100.00"), "BRL"),
            DepositMoneyResponse.class);

        // Withdraw 30
        ResponseEntity<WithdrawMoneyResponse> resp = restTemplate.postForEntity(
            "/api/v1/accounts/" + accountId + "/withdraw",
            new WithdrawMoneyRequest(new BigDecimal("30.00"), "BRL"),
            WithdrawMoneyResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().balance()).isEqualByComparingTo("70.00");

        List<DomainEvent> events = eventStoreRepository.load(accountId);
        assertThat(events).hasSize(3);
        MoneyWithdrawn withdrawn = (MoneyWithdrawn) events.get(2);
        assertThat(withdrawn.sequenceNumber()).isEqualTo(3L);
        assertThat(withdrawn.amount()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("POST /withdraw on insufficient balance returns 422 and persists NO MoneyWithdrawn event")
    void post_withdraw_insufficientBalance_returns422_persistsNoEvent() {
        UUID accountId = restTemplate.postForObject(
            "/api/v1/accounts", new CreateAccountRequest("owner-overdraft-it"),
            CreateAccountResponse.class).accountId();
        // No deposit — balance is zero

        ResponseEntity<String> resp = restTemplate.postForEntity(
            "/api/v1/accounts/" + accountId + "/withdraw",
            new WithdrawMoneyRequest(new BigDecimal("10.00"), "BRL"),
            String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(422);
        assertThat(resp.getBody()).contains("INSUFFICIENT_FUNDS");

        // Only the AccountCreated event should exist — no MoneyWithdrawn was persisted
        List<DomainEvent> events = eventStoreRepository.load(accountId);
        assertThat(events).hasSize(1);
    }
}
