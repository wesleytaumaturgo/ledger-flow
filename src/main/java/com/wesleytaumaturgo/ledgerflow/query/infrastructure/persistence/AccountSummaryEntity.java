package com.wesleytaumaturgo.ledgerflow.query.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the account_summary read model (denormalized, query-optimized).
 * Infrastructure data carrier — zero business logic.
 * No public setters: package-private setters for mapper use only.
 * No OneToMany/ManyToOne: denormalized table; no joins needed.
 */
@Entity
@Table(name = "account_summary")
class AccountSummaryEntity {

    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBalance;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "total_deposited", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDeposited;

    @Column(name = "total_withdrawn", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalWithdrawn;

    @Column(name = "transaction_count", nullable = false)
    private int transactionCount;

    @Column(name = "last_event_sequence", nullable = false)
    private long lastEventSequence;

    @Column(name = "last_transaction_at")
    private Instant lastTransactionAt;

    // JPA requires a no-arg constructor
    AccountSummaryEntity() {}

    // Getters

    UUID getAccountId() {
        return accountId;
    }

    String getOwnerId() {
        return ownerId;
    }

    BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    String getCurrency() {
        return currency;
    }

    BigDecimal getTotalDeposited() {
        return totalDeposited;
    }

    BigDecimal getTotalWithdrawn() {
        return totalWithdrawn;
    }

    int getTransactionCount() {
        return transactionCount;
    }

    long getLastEventSequence() {
        return lastEventSequence;
    }

    Instant getLastTransactionAt() {
        return lastTransactionAt;
    }

    // Package-private setters — for use by mapper and projector only

    void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
    }

    void setCurrency(String currency) {
        this.currency = currency;
    }

    void setTotalDeposited(BigDecimal totalDeposited) {
        this.totalDeposited = totalDeposited;
    }

    void setTotalWithdrawn(BigDecimal totalWithdrawn) {
        this.totalWithdrawn = totalWithdrawn;
    }

    void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }

    void setLastEventSequence(long lastEventSequence) {
        this.lastEventSequence = lastEventSequence;
    }

    void setLastTransactionAt(Instant lastTransactionAt) {
        this.lastTransactionAt = lastTransactionAt;
    }
}
