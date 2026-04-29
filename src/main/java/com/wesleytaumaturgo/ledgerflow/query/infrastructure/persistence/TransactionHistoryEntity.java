package com.wesleytaumaturgo.ledgerflow.query.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the transaction_history read model (denormalized, query-optimized).
 * Infrastructure data carrier — zero business logic.
 * No public setters: package-private setters for mapper and projector use only.
 * No OneToMany/ManyToOne: denormalized table; no joins needed.
 */
@Entity
@Table(name = "transaction_history")
class TransactionHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "event_type", length = 50)
    private String eventType;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "counterparty_account_id")
    private UUID counterpartyAccountId;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    // JPA requires a no-arg constructor
    TransactionHistoryEntity() {}

    // Getters

    UUID getId() {
        return id;
    }

    UUID getAccountId() {
        return accountId;
    }

    String getEventType() {
        return eventType;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    String getDescription() {
        return description;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }

    UUID getCounterpartyAccountId() {
        return counterpartyAccountId;
    }

    long getSequenceNumber() {
        return sequenceNumber;
    }

    // Package-private setters — for use by mapper and projector only

    void setId(UUID id) {
        this.id = id;
    }

    void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    void setEventType(String eventType) {
        this.eventType = eventType;
    }

    void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    void setCurrency(String currency) {
        this.currency = currency;
    }

    void setDescription(String description) {
        this.description = description;
    }

    void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    void setCounterpartyAccountId(UUID counterpartyAccountId) {
        this.counterpartyAccountId = counterpartyAccountId;
    }

    void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
}
