package com.wesleytaumaturgo.ledgerflow.query.infrastructure.persistence;

import com.wesleytaumaturgo.ledgerflow.query.application.usecase.BalanceView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AccountSummaryMapper — entity to view mapping.
 * No Spring context needed — pure Java mapper.
 */
class AccountSummaryMapperTest {

    private final AccountSummaryMapper mapper = new AccountSummaryMapper();

    @Test
    @DisplayName("toView maps all fields from entity to BalanceView")
    void toView_allFields() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        Instant lastTransactionAt = Instant.parse("2026-04-01T10:00:00Z");

        AccountSummaryEntity entity = new AccountSummaryEntity();
        entity.setAccountId(accountId);
        entity.setOwnerId("owner-123");
        entity.setCurrentBalance(new BigDecimal("1500.75"));
        entity.setCurrency("BRL");
        entity.setTotalDeposited(new BigDecimal("2000.00"));
        entity.setTotalWithdrawn(new BigDecimal("499.25"));
        entity.setTransactionCount(5);
        entity.setLastEventSequence(5L);
        entity.setLastTransactionAt(lastTransactionAt);

        // Act
        BalanceView view = mapper.toView(entity);

        // Assert
        assertThat(view.accountId()).isEqualTo(accountId);
        assertThat(view.ownerId()).isEqualTo("owner-123");
        assertThat(view.balance()).isEqualByComparingTo(new BigDecimal("1500.75"));
        assertThat(view.currency()).isEqualTo("BRL");
        assertThat(view.transactionCount()).isEqualTo(5);
        assertThat(view.lastEventSequence()).isEqualTo(5L);
        assertThat(view.totalDeposited()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(view.totalWithdrawn()).isEqualByComparingTo(new BigDecimal("499.25"));
        assertThat(view.lastTransactionAt()).isEqualTo(lastTransactionAt);
    }

    @Test
    @DisplayName("toView maps null lastTransactionAt without throwing")
    void toView_nullOptionalField() {
        // Arrange
        UUID accountId = UUID.randomUUID();

        AccountSummaryEntity entity = new AccountSummaryEntity();
        entity.setAccountId(accountId);
        entity.setOwnerId("owner-456");
        entity.setCurrentBalance(BigDecimal.ZERO);
        entity.setCurrency("BRL");
        entity.setTotalDeposited(BigDecimal.ZERO);
        entity.setTotalWithdrawn(BigDecimal.ZERO);
        entity.setTransactionCount(0);
        entity.setLastEventSequence(1L);
        entity.setLastTransactionAt(null);

        // Act
        BalanceView view = mapper.toView(entity);

        // Assert
        assertThat(view.accountId()).isEqualTo(accountId);
        assertThat(view.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(view.lastTransactionAt()).isNull();
    }
}
