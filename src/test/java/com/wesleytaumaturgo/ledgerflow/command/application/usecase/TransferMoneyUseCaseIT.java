package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import com.wesleytaumaturgo.ledgerflow.IntegrationTestBase;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.TransferMoneyRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.TransferMoneyResponse;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.TransferCompleted;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.TransferDirection;
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

class TransferMoneyUseCaseIT extends IntegrationTestBase {

    @Autowired
    private EventStoreRepository eventStoreRepository;

    @Test
    @DisplayName("POST /transfer writes TransferCompleted DEBIT to source and CREDIT to target")
    void post_transfer_persistsBothTransferCompletedEvents() {
        // Seed source with 100 BRL
        UUID sourceId = restTemplate.postForObject(
            "/api/v1/accounts", new CreateAccountRequest("source-owner"),
            CreateAccountResponse.class).accountId();
        restTemplate.postForObject(
            "/api/v1/accounts/" + sourceId + "/deposit",
            new DepositMoneyRequest(new BigDecimal("100.00"), "BRL"),
            DepositMoneyResponse.class);

        // Seed target (zero balance)
        UUID targetId = restTemplate.postForObject(
            "/api/v1/accounts", new CreateAccountRequest("target-owner"),
            CreateAccountResponse.class).accountId();

        // Transfer 30 from source to target
        ResponseEntity<TransferMoneyResponse> resp = restTemplate.postForEntity(
            "/api/v1/accounts/" + sourceId + "/transfer",
            new TransferMoneyRequest(targetId, new BigDecimal("30.00"), "BRL"),
            TransferMoneyResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Source: AccountCreated, MoneyDeposited, TransferCompleted DEBIT (3 events)
        List<DomainEvent> sourceEvents = eventStoreRepository.load(sourceId);
        assertThat(sourceEvents).hasSize(3);
        assertThat(sourceEvents.get(2)).isInstanceOf(TransferCompleted.class);
        TransferCompleted debit = (TransferCompleted) sourceEvents.get(2);
        assertThat(debit.direction()).isEqualTo(TransferDirection.DEBIT);
        assertThat(debit.counterpartId()).isEqualTo(targetId);
        assertThat(debit.amount()).isEqualByComparingTo("30.00");
        assertThat(debit.sequenceNumber()).isEqualTo(3L);

        // Target: AccountCreated, TransferCompleted CREDIT (2 events)
        List<DomainEvent> targetEvents = eventStoreRepository.load(targetId);
        assertThat(targetEvents).hasSize(2);
        TransferCompleted credit = (TransferCompleted) targetEvents.get(1);
        assertThat(credit.direction()).isEqualTo(TransferDirection.CREDIT);
        assertThat(credit.counterpartId()).isEqualTo(sourceId);
        assertThat(credit.sequenceNumber()).isEqualTo(2L);
    }

    @Test
    @DisplayName("POST /transfer with source == target returns 422 SELF_TRANSFER_NOT_ALLOWED")
    void post_transfer_selfTransfer_returns422() {
        UUID accountId = restTemplate.postForObject(
            "/api/v1/accounts", new CreateAccountRequest("self-owner"),
            CreateAccountResponse.class).accountId();

        ResponseEntity<String> resp = restTemplate.postForEntity(
            "/api/v1/accounts/" + accountId + "/transfer",
            new TransferMoneyRequest(accountId, new BigDecimal("10.00"), "BRL"),
            String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(422);
        assertThat(resp.getBody()).contains("SELF_TRANSFER_NOT_ALLOWED");
    }

    @Test
    @DisplayName("POST /transfer on missing target returns 404 ACCOUNT_NOT_FOUND")
    void post_transfer_targetMissing_returns404() {
        UUID sourceId = restTemplate.postForObject(
            "/api/v1/accounts", new CreateAccountRequest("source-only"),
            CreateAccountResponse.class).accountId();
        restTemplate.postForObject(
            "/api/v1/accounts/" + sourceId + "/deposit",
            new DepositMoneyRequest(new BigDecimal("100.00"), "BRL"),
            DepositMoneyResponse.class);
        UUID nonexistentTarget = UUID.randomUUID();

        ResponseEntity<String> resp = restTemplate.postForEntity(
            "/api/v1/accounts/" + sourceId + "/transfer",
            new TransferMoneyRequest(nonexistentTarget, new BigDecimal("10.00"), "BRL"),
            String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        assertThat(resp.getBody()).contains("ACCOUNT_NOT_FOUND");
    }
}
