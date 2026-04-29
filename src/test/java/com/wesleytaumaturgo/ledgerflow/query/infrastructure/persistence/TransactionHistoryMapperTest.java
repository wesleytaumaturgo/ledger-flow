package com.wesleytaumaturgo.ledgerflow.query.infrastructure.persistence;

import com.wesleytaumaturgo.ledgerflow.query.application.usecase.TransactionHistoryView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TransactionHistoryMapper — entity to view mapping.
 * No Spring context needed — pure Java mapper.
 */
class TransactionHistoryMapperTest {

    private final TransactionHistoryMapper mapper = new TransactionHistoryMapper();

    @Test
    @DisplayName("toView maps all fields including counterpartyAccountId")
    void toView_allFields() {
        // Arrange
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID counterpartyId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-04-01T12:00:00Z");

        TransactionHistoryEntity entity = new TransactionHistoryEntity();
        entity.setId(id);
        entity.setAccountId(accountId);
        entity.setEventType("MoneyDeposited");
        entity.setAmount(new BigDecimal("500.00"));
        entity.setCurrency("BRL");
        entity.setDescription("Deposit via PIX");
        entity.setOccurredAt(occurredAt);
        entity.setCounterpartyAccountId(counterpartyId);
        entity.setSequenceNumber(3L);

        // Act
        TransactionHistoryView view = mapper.toView(entity);

        // Assert
        assertThat(view.id()).isEqualTo(id);
        assertThat(view.accountId()).isEqualTo(accountId);
        assertThat(view.eventType()).isEqualTo("MoneyDeposited");
        assertThat(view.amount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(view.currency()).isEqualTo("BRL");
        assertThat(view.description()).isEqualTo("Deposit via PIX");
        assertThat(view.occurredAt()).isEqualTo(occurredAt);
        assertThat(view.counterpartyAccountId()).isEqualTo(counterpartyId);
    }

    @Test
    @DisplayName("toView maps null counterpartyAccountId without throwing")
    void toView_nullCounterparty() {
        // Arrange
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-04-02T08:30:00Z");

        TransactionHistoryEntity entity = new TransactionHistoryEntity();
        entity.setId(id);
        entity.setAccountId(accountId);
        entity.setEventType("MoneyWithdrawn");
        entity.setAmount(new BigDecimal("200.00"));
        entity.setCurrency("BRL");
        entity.setDescription("ATM withdrawal");
        entity.setOccurredAt(occurredAt);
        entity.setCounterpartyAccountId(null);
        entity.setSequenceNumber(7L);

        // Act
        TransactionHistoryView view = mapper.toView(entity);

        // Assert
        assertThat(view.id()).isEqualTo(id);
        assertThat(view.accountId()).isEqualTo(accountId);
        assertThat(view.counterpartyAccountId()).isNull();
        assertThat(view.amount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }
}
