package com.wesleytaumaturgo.ledgerflow.command.domain.model;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.InsufficientFundsException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.InvalidAmountException;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.AccountCreated;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyDeposited;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.MoneyWithdrawn;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.TransferCompleted;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.event.TransferDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the event-sourced Account aggregate. No Spring context, no database.
 * Covers: createAccount, deposit, withdraw, debitTransfer, creditTransfer,
 * invariant violations (overdraft, non-positive amount), pullDomainEvents semantics,
 * and reconstitute (empty list rejection and replay behavior).
 */
class AccountTest {

    private static final String BRL = "BRL";
    private static final String OWNER_ID = "owner-1";

    // ---------------- createAccount ----------------

    @Test
    @DisplayName("createAccount initializes balance to zero, version 1, status ACTIVE, and emits AccountCreated")
    void createAccount_initializesState_andEmitsAccountCreated() {
        AccountId id = AccountId.generate();

        Account account = Account.createAccount(id, OWNER_ID);
        List<DomainEvent> events = account.pullDomainEvents();

        assertThat(account.id()).isEqualTo(id);
        assertThat(account.ownerId()).isEqualTo(OWNER_ID);
        assertThat(account.balance()).isEqualTo(Money.zero(BRL));
        assertThat(account.version()).isEqualTo(1L);
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.isActive()).isTrue();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AccountCreated.class);
        AccountCreated created = (AccountCreated) events.get(0);
        assertThat(created.sequenceNumber()).isEqualTo(1L);
        assertThat(created.accountId()).isEqualTo(id.value());
        assertThat(created.ownerId()).isEqualTo(OWNER_ID);
    }

    @Test
    @DisplayName("createAccount with null id throws NullPointerException")
    void createAccount_nullId_throwsNullPointerException() {
        assertThatThrownBy(() -> Account.createAccount(null, OWNER_ID))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("createAccount with null ownerId throws NullPointerException")
    void createAccount_nullOwnerId_throwsNullPointerException() {
        assertThatThrownBy(() -> Account.createAccount(AccountId.generate(), null))
            .isInstanceOf(NullPointerException.class);
    }

    // ---------------- deposit ----------------

    @Test
    @DisplayName("deposit on fresh account increments balance and emits MoneyDeposited with sequenceNumber 2")
    void deposit_onFreshAccount_incrementsBalance_andEmitsEvent() {
        Account account = Account.createAccount(AccountId.generate(), OWNER_ID);
        account.pullDomainEvents();   // drain createAccount event

        account.deposit(Money.of(new BigDecimal("100.00"), BRL));
        List<DomainEvent> events = account.pullDomainEvents();

        assertThat(account.balance().amount()).isEqualByComparingTo("100.00");
        assertThat(account.version()).isEqualTo(2L);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(MoneyDeposited.class);
        MoneyDeposited deposited = (MoneyDeposited) events.get(0);
        assertThat(deposited.sequenceNumber()).isEqualTo(2L);
        assertThat(deposited.amount()).isEqualByComparingTo("100.00");
        assertThat(deposited.currency()).isEqualTo(BRL);
    }

    // ---------------- withdraw ----------------

    @Test
    @DisplayName("withdraw with sufficient balance reduces balance and emits MoneyWithdrawn")
    void withdraw_sufficientBalance_reducesBalance_andEmitsEvent() {
        Account account = anAccountWithBalance("100.00");

        account.withdraw(Money.of(new BigDecimal("30.00"), BRL));
        List<DomainEvent> events = account.pullDomainEvents();

        assertThat(account.balance().amount()).isEqualByComparingTo("70.00");
        assertThat(events).hasSize(1).first().isInstanceOf(MoneyWithdrawn.class);
    }

    @Test
    @DisplayName("withdraw with insufficient balance throws InsufficientFundsException and buffers NO event")
    void withdraw_insufficientBalance_throwsInsufficientFunds_andBuffersNoEvent() {
        Account account = anAccountWithBalance("50.00");

        assertThatThrownBy(() -> account.withdraw(Money.of(new BigDecimal("100.00"), BRL)))
            .isInstanceOf(InsufficientFundsException.class)
            .hasMessageContaining("Insufficient funds");

        assertThat(account.balance().amount()).isEqualByComparingTo("50.00");   // unchanged
        assertThat(account.pullDomainEvents()).isEmpty();                         // no event buffered
    }

    // ---------------- debitTransfer ----------------

    @Test
    @DisplayName("debitTransfer reduces balance and emits TransferCompleted with direction=DEBIT")
    void debitTransfer_reducesBalance_andEmitsDebitEvent() {
        Account source = anAccountWithBalance("100.00");
        AccountId targetId = AccountId.generate();

        source.debitTransfer(Money.of(new BigDecimal("40.00"), BRL), targetId);
        List<DomainEvent> events = source.pullDomainEvents();

        assertThat(source.balance().amount()).isEqualByComparingTo("60.00");
        assertThat(events).hasSize(1);
        TransferCompleted event = (TransferCompleted) events.get(0);
        assertThat(event.direction()).isEqualTo(TransferDirection.DEBIT);
        assertThat(event.counterpartId()).isEqualTo(targetId.value());
        assertThat(event.amount()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("debitTransfer with insufficient balance throws InsufficientFundsException and buffers NO event")
    void debitTransfer_insufficientBalance_throwsInsufficientFunds_andBuffersNoEvent() {
        Account source = anAccountWithBalance("10.00");

        assertThatThrownBy(() -> source.debitTransfer(
            Money.of(new BigDecimal("100.00"), BRL), AccountId.generate()))
            .isInstanceOf(InsufficientFundsException.class);

        assertThat(source.balance().amount()).isEqualByComparingTo("10.00");   // unchanged
        assertThat(source.pullDomainEvents()).isEmpty();
    }

    // ---------------- creditTransfer ----------------

    @Test
    @DisplayName("creditTransfer increases balance and emits TransferCompleted with direction=CREDIT")
    void creditTransfer_increasesBalance_andEmitsCreditEvent() {
        Account target = anAccountWithBalance("100.00");
        AccountId sourceId = AccountId.generate();

        target.creditTransfer(Money.of(new BigDecimal("25.00"), BRL), sourceId);
        List<DomainEvent> events = target.pullDomainEvents();

        assertThat(target.balance().amount()).isEqualByComparingTo("125.00");
        assertThat(events).hasSize(1);
        TransferCompleted event = (TransferCompleted) events.get(0);
        assertThat(event.direction()).isEqualTo(TransferDirection.CREDIT);
        assertThat(event.counterpartId()).isEqualTo(sourceId.value());
        assertThat(event.amount()).isEqualByComparingTo("25.00");
    }

    // ---------------- pullDomainEvents semantics ----------------

    @Test
    @DisplayName("pullDomainEvents drains buffer — second call returns empty list")
    void pullDomainEvents_drains_secondCallReturnsEmpty() {
        Account account = Account.createAccount(AccountId.generate(), OWNER_ID);

        List<DomainEvent> first = account.pullDomainEvents();
        List<DomainEvent> second = account.pullDomainEvents();

        assertThat(first).hasSize(1);
        assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("pullDomainEvents returns immutable copy — caller cannot mutate buffer via returned list")
    void pullDomainEvents_returnsImmutableCopy_callerCannotMutateBuffer() {
        Account account = Account.createAccount(AccountId.generate(), OWNER_ID);

        List<DomainEvent> events = account.pullDomainEvents();

        // List.copyOf returns an unmodifiable list — any mutation attempt throws
        assertThatThrownBy(() -> events.add(null))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------------- reconstitute ----------------

    @Test
    @DisplayName("reconstitute with empty list throws IllegalArgumentException containing 'empty'")
    void reconstitute_emptyList_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Account.reconstitute(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("reconstitute applies events in order and does NOT buffer them as uncommitted")
    void reconstitute_appliesEvents_inOrder_doesNotBufferAsUncommitted() {
        UUID id = UUID.randomUUID();
        AccountCreated created = new AccountCreated(
            UUID.randomUUID(), id, OWNER_ID,
            java.time.Instant.parse("2026-01-01T10:00:00Z"), 1L);
        MoneyDeposited deposited = new MoneyDeposited(
            UUID.randomUUID(), id, new BigDecimal("100.00"), BRL,
            java.time.Instant.parse("2026-01-01T10:01:00Z"), 2L);

        Account account = Account.reconstitute(List.of(created, deposited));

        assertThat(account.balance().amount()).isEqualByComparingTo("100.00");
        assertThat(account.version()).isEqualTo(2L);
        // Replayed events are NOT "new" facts — uncommitted buffer must be empty
        assertThat(account.pullDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("reconstitute with only AccountCreated yields zero balance and version 1")
    void reconstitute_onlyCreatedEvent_yieldsZeroBalanceAndVersion1() {
        UUID id = UUID.randomUUID();
        AccountCreated created = new AccountCreated(
            UUID.randomUUID(), id, OWNER_ID,
            java.time.Instant.parse("2026-01-01T10:00:00Z"), 1L);

        Account account = Account.reconstitute(List.of(created));

        assertThat(account.balance()).isEqualTo(Money.zero(BRL));
        assertThat(account.version()).isEqualTo(1L);
        assertThat(account.id().value()).isEqualTo(id);
    }

    // ---------------- Fixture factory ----------------

    /**
     * Returns an Account that has already had createAccount + a single deposit applied,
     * with the uncommitted event buffer drained — so individual tests start from a known state.
     */
    private static Account anAccountWithBalance(String amount) {
        Account account = Account.createAccount(AccountId.generate(), OWNER_ID);
        account.deposit(Money.of(new BigDecimal(amount), BRL));
        account.pullDomainEvents();
        return account;
    }
}
