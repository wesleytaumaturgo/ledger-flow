package com.wesleytaumaturgo.ledgerflow.command.domain.exception;

import com.wesleytaumaturgo.ledgerflow.command.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the domain exception hierarchy.
 * Verifies stable errorCode(), correct httpStatus(), and human-readable messages.
 * Required by FORGE §4.4 and domain-exception-hierarchy rule 9.
 */
class DomainExceptionTest {

    // AccountNotFoundException

    @Test
    @DisplayName("AccountNotFoundException errorCode is stable ACCOUNT_NOT_FOUND")
    void accountNotFound_errorCode() {
        UUID id = UUID.randomUUID();
        AccountNotFoundException ex = new AccountNotFoundException(id);
        assertThat(ex.errorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    @DisplayName("AccountNotFoundException httpStatus is 404")
    void accountNotFound_httpStatus() {
        UUID id = UUID.randomUUID();
        AccountNotFoundException ex = new AccountNotFoundException(id);
        assertThat(ex.httpStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("AccountNotFoundException message contains accountId")
    void accountNotFound_message() {
        UUID id = UUID.randomUUID();
        AccountNotFoundException ex = new AccountNotFoundException(id);
        assertThat(ex.getMessage()).contains(id.toString());
    }

    // AccountInactiveException

    @Test
    @DisplayName("AccountInactiveException errorCode is stable ACCOUNT_INACTIVE")
    void accountInactive_errorCode() {
        UUID id = UUID.randomUUID();
        AccountInactiveException ex = new AccountInactiveException(id);
        assertThat(ex.errorCode()).isEqualTo("ACCOUNT_INACTIVE");
    }

    @Test
    @DisplayName("AccountInactiveException httpStatus is 422")
    void accountInactive_httpStatus() {
        UUID id = UUID.randomUUID();
        AccountInactiveException ex = new AccountInactiveException(id);
        assertThat(ex.httpStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("AccountInactiveException message contains accountId")
    void accountInactive_message() {
        UUID id = UUID.randomUUID();
        AccountInactiveException ex = new AccountInactiveException(id);
        assertThat(ex.getMessage()).contains(id.toString());
    }

    // InsufficientFundsException

    @Test
    @DisplayName("InsufficientFundsException errorCode is stable INSUFFICIENT_FUNDS")
    void insufficientFunds_errorCode() {
        Money requested = Money.of(new BigDecimal("500.00"), "BRL");
        Money available = Money.of(new BigDecimal("100.00"), "BRL");
        InsufficientFundsException ex = new InsufficientFundsException(requested, available);
        assertThat(ex.errorCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("InsufficientFundsException httpStatus is 422")
    void insufficientFunds_httpStatus() {
        Money requested = Money.of(new BigDecimal("500.00"), "BRL");
        Money available = Money.of(new BigDecimal("100.00"), "BRL");
        InsufficientFundsException ex = new InsufficientFundsException(requested, available);
        assertThat(ex.httpStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("InsufficientFundsException message is human-readable with amounts")
    void insufficientFunds_message() {
        Money requested = Money.of(new BigDecimal("500.00"), "BRL");
        Money available = Money.of(new BigDecimal("100.00"), "BRL");
        InsufficientFundsException ex = new InsufficientFundsException(requested, available);
        assertThat(ex.getMessage()).contains("500").contains("100");
    }

    // InvalidAmountException

    @Test
    @DisplayName("InvalidAmountException errorCode is stable INVALID_AMOUNT")
    void invalidAmount_errorCode() {
        InvalidAmountException ex = new InvalidAmountException("amount must be positive, got: 0");
        assertThat(ex.errorCode()).isEqualTo("INVALID_AMOUNT");
    }

    @Test
    @DisplayName("InvalidAmountException httpStatus is 422")
    void invalidAmount_httpStatus() {
        InvalidAmountException ex = new InvalidAmountException("amount must be positive, got: 0");
        assertThat(ex.httpStatus()).isEqualTo(422);
    }

    // OptimisticLockException

    @Test
    @DisplayName("OptimisticLockException errorCode is stable OPTIMISTIC_LOCK_CONFLICT")
    void optimisticLock_errorCode() {
        UUID id = UUID.randomUUID();
        OptimisticLockException ex = new OptimisticLockException(id);
        assertThat(ex.errorCode()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
    }

    @Test
    @DisplayName("OptimisticLockException httpStatus is 409")
    void optimisticLock_httpStatus() {
        UUID id = UUID.randomUUID();
        OptimisticLockException ex = new OptimisticLockException(id);
        assertThat(ex.httpStatus()).isEqualTo(409);
    }

    // SelfTransferNotAllowedException

    @Test
    @DisplayName("SelfTransferNotAllowedException errorCode is stable SELF_TRANSFER_NOT_ALLOWED")
    void selfTransfer_errorCode() {
        UUID id = UUID.randomUUID();
        SelfTransferNotAllowedException ex = new SelfTransferNotAllowedException(id);
        assertThat(ex.errorCode()).isEqualTo("SELF_TRANSFER_NOT_ALLOWED");
    }

    @Test
    @DisplayName("SelfTransferNotAllowedException httpStatus is 422")
    void selfTransfer_httpStatus() {
        UUID id = UUID.randomUUID();
        SelfTransferNotAllowedException ex = new SelfTransferNotAllowedException(id);
        assertThat(ex.httpStatus()).isEqualTo(422);
    }
}
