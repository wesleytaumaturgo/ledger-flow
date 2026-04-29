package com.wesleytaumaturgo.ledgerflow.command.domain.model;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.InsufficientFundsException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.InvalidAmountException;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.AccountCreated;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyDeposited;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyWithdrawn;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.TransferCompleted;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.TransferDirection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Event-sourced Account aggregate root.
 *
 * State is NEVER persisted directly. Every state change produces a DomainEvent
 * stored append-only in event_store. Current state is reconstituted by replaying
 * all events for an aggregateId via {@link #reconstitute(List)}.
 *
 * Invariants enforced (REQ-reject-overdraft / FR-005, REQ-reject-non-positive-amount / FR-006):
 *   - deposit / withdraw / transfer amount must be positive (Money.of validates)
 *   - withdraw / debitTransfer require balance >= amount or InsufficientFundsException
 *
 * Determinism (REQ-replay-determinism / FR-016, REQ-nfr-no-instant-in-apply / NFR-006):
 *   - apply*() methods MUST NOT call Instant.now() — timestamps come from event.occurredAt()
 *   - reconstitute(events) replay produces identical state on repeated calls with same input
 *
 * Pure Java per CLAUDE.md §3.1 — zero Spring/JPA/Jackson imports.
 */
public final class Account {

    private AccountId id;
    private String ownerId;
    private Money balance;
    private AccountStatus status;
    private long version;
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

    private Account() {
        // Private — instances created via static factories only
    }

    /**
     * Factory for a new Account. Emits a single AccountCreated event with sequenceNumber=1.
     * Initial balance is Money.zero("BRL"); status is AccountStatus.ACTIVE (D-01).
     *
     * Instant.now() is allowed HERE because this is a business operation, NOT an apply method.
     * REQ-account-create / FR-001.
     */
    public static Account createAccount(AccountId id, String ownerId) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");

        Account account = new Account();
        AccountCreated event = new AccountCreated(
            UUID.randomUUID(),
            id.value(),
            ownerId,
            Instant.now(),
            1L
        );
        account.apply(event);
        account.uncommittedEvents.add(event);
        return account;
    }

    /**
     * Reconstitute aggregate state by replaying events in order.
     * REQ-reconstitute-from-history / FR-017.
     *
     * Empty list throws IllegalArgumentException — an account must have at least its
     * AccountCreated event to exist. apply() is called for each event without buffering
     * (replay does not produce new uncommitted events).
     */
    public static Account reconstitute(List<DomainEvent> events) {
        Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) {
            throw new IllegalArgumentException("Cannot reconstitute Account from empty event list");
        }
        Account account = new Account();
        for (DomainEvent event : events) {
            account.apply(event);
        }
        // uncommittedEvents stays empty — replay events are not "new" facts to publish
        return account;
    }

    // ---------------- Business operations ----------------

    /**
     * Deposit money into the account. REQ-money-deposit / FR-002.
     * Validates amount positive (Money.of throws InvalidAmountException on zero/negative).
     */
    public void deposit(Money amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.isZeroOrNegative()) {
            throw new InvalidAmountException("Deposit amount must be positive, got: " + amount.amount());
        }
        MoneyDeposited event = new MoneyDeposited(
            UUID.randomUUID(),
            id.value(),
            amount.amount(),
            amount.currency(),
            Instant.now(),
            nextVersion()
        );
        apply(event);
        uncommittedEvents.add(event);
    }

    /**
     * Withdraw money from the account. REQ-money-withdraw / FR-003 + REQ-reject-overdraft / FR-005.
     * Validates amount positive AND balance sufficient — otherwise throws exception with no event produced.
     */
    public void withdraw(Money amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.isZeroOrNegative()) {
            throw new InvalidAmountException("Withdraw amount must be positive, got: " + amount.amount());
        }
        if (balance.isLessThan(amount)) {
            throw new InsufficientFundsException(amount, balance);
        }
        MoneyWithdrawn event = new MoneyWithdrawn(
            UUID.randomUUID(),
            id.value(),
            amount.amount(),
            amount.currency(),
            Instant.now(),
            nextVersion()
        );
        apply(event);
        uncommittedEvents.add(event);
    }

    /**
     * Debit-side of a transfer. Reduces this account's balance.
     * Used by TransferMoneyUseCase on the source account (CONTEXT.md discretion).
     * REQ-money-transfer / FR-004 + REQ-reject-overdraft / FR-005.
     */
    public void debitTransfer(Money amount, AccountId targetId) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        if (amount.isZeroOrNegative()) {
            throw new InvalidAmountException("Transfer amount must be positive, got: " + amount.amount());
        }
        if (balance.isLessThan(amount)) {
            throw new InsufficientFundsException(amount, balance);
        }
        TransferCompleted event = new TransferCompleted(
            UUID.randomUUID(),
            id.value(),
            targetId.value(),
            amount.amount(),
            amount.currency(),
            TransferDirection.DEBIT,
            Instant.now(),
            nextVersion()
        );
        apply(event);
        uncommittedEvents.add(event);
    }

    /**
     * Credit-side of a transfer. Increases this account's balance.
     * Used by TransferMoneyUseCase on the target account.
     * REQ-money-transfer / FR-004.
     */
    public void creditTransfer(Money amount, AccountId sourceId) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        if (amount.isZeroOrNegative()) {
            throw new InvalidAmountException("Transfer amount must be positive, got: " + amount.amount());
        }
        TransferCompleted event = new TransferCompleted(
            UUID.randomUUID(),
            id.value(),
            sourceId.value(),
            amount.amount(),
            amount.currency(),
            TransferDirection.CREDIT,
            Instant.now(),
            nextVersion()
        );
        apply(event);
        uncommittedEvents.add(event);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    // ---------------- Apply methods (pure state mutation) ----------------
    // CLAUDE.md §3.6: NO Instant.now() here. NO validation. NO side effects.
    // Timestamps come from event.occurredAt(), version comes from event.sequenceNumber().

    private void apply(DomainEvent event) {
        switch (event) {
            case AccountCreated e    -> applyAccountCreated(e);
            case MoneyDeposited e    -> applyMoneyDeposited(e);
            case MoneyWithdrawn e    -> applyMoneyWithdrawn(e);
            case TransferCompleted e -> applyTransferCompleted(e);
            default -> throw new IllegalStateException(
                "Unknown event type for Account: " + event.getClass().getSimpleName());
        }
    }

    private void applyAccountCreated(AccountCreated event) {
        this.id = AccountId.of(event.accountId());
        this.ownerId = event.ownerId();
        this.balance = Money.zero("BRL");
        this.status = AccountStatus.ACTIVE;
        this.version = event.sequenceNumber();
    }

    private void applyMoneyDeposited(MoneyDeposited event) {
        this.balance = balance.add(Money.of(event.amount(), event.currency()));
        this.version = event.sequenceNumber();
    }

    private void applyMoneyWithdrawn(MoneyWithdrawn event) {
        this.balance = balance.subtract(Money.of(event.amount(), event.currency()));
        this.version = event.sequenceNumber();
    }

    private void applyTransferCompleted(TransferCompleted event) {
        Money transferAmount = Money.of(event.amount(), event.currency());
        if (event.direction() == TransferDirection.DEBIT) {
            this.balance = balance.subtract(transferAmount);
        } else {
            this.balance = balance.add(transferAmount);
        }
        this.version = event.sequenceNumber();
    }

    private long nextVersion() {
        return version + 1;
    }

    // ---------------- Buffer management ----------------

    /**
     * Drain uncommitted events. Returns a defensive copy and clears the internal buffer.
     * Called by use cases AFTER successful event-store save (CLAUDE.md aggregate rule 7).
     */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> snapshot = List.copyOf(uncommittedEvents);
        uncommittedEvents.clear();
        return snapshot;
    }

    // ---------------- Read-only accessors ----------------

    public AccountId id()           { return id; }
    public String ownerId()         { return ownerId; }
    public Money balance()          { return balance; }
    public AccountStatus status()   { return status; }
    public long version()           { return version; }
}
