package com.wesleytaumaturgo.ledgerflow.api;

import com.wesleytaumaturgo.ledgerflow.command.application.usecase.CreateAccountResult;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.CreateAccountUseCase;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.DepositMoneyResult;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.DepositMoneyUseCase;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.TransferMoneyResult;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.TransferMoneyUseCase;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.WithdrawMoneyResult;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.WithdrawMoneyUseCase;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.AccountNotFoundException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.InsufficientFundsException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.InvalidAmountException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.OptimisticLockException;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.SelfTransferNotAllowedException;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountCommandController.class)
class AccountCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private CreateAccountUseCase createAccountUseCase;
    @MockBean private DepositMoneyUseCase  depositMoneyUseCase;
    @MockBean private WithdrawMoneyUseCase withdrawMoneyUseCase;
    @MockBean private TransferMoneyUseCase transferMoneyUseCase;

    // ---------------- POST /api/v1/accounts ----------------

    @Test
    @DisplayName("POST /api/v1/accounts returns 201 with accountId body and Location header on success")
    void createAccount_success_returns201_withLocationHeader() throws Exception {
        UUID accountId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(createAccountUseCase.execute(any())).thenReturn(new CreateAccountResult(accountId));

        mockMvc.perform(post("/api/v1/accounts")
                .contentType(APPLICATION_JSON)
                .content("{\"ownerId\":\"owner-1\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/accounts/" + accountId))
            .andExpect(jsonPath("$.accountId").value(accountId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/accounts with blank ownerId returns 400 VALIDATION_ERROR")
    void createAccount_blankOwnerId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(APPLICATION_JSON)
                .content("{\"ownerId\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/accounts with missing ownerId returns 400 VALIDATION_ERROR")
    void createAccount_missingOwnerId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // ---------------- POST /api/v1/accounts/{id}/deposit ----------------

    @Test
    @DisplayName("POST /deposit returns 200 with balance on success")
    void deposit_success_returns200() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(depositMoneyUseCase.execute(any()))
            .thenReturn(new DepositMoneyResult(accountId, new BigDecimal("150.00"), "BRL"));

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", accountId)
                .contentType(APPLICATION_JSON)
                .content("{\"amount\":50.00,\"currency\":\"BRL\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId.toString()))
            .andExpect(jsonPath("$.balance").value(150.00))
            .andExpect(jsonPath("$.currency").value("BRL"));
    }

    @Test
    @DisplayName("POST /deposit with zero amount returns 422 INVALID_AMOUNT")
    void deposit_zeroAmount_returns422() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(depositMoneyUseCase.execute(any()))
            .thenThrow(new InvalidAmountException("amount must be positive, got: 0"));

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", accountId)
                .contentType(APPLICATION_JSON)
                .content("{\"amount\":0,\"currency\":\"BRL\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("INVALID_AMOUNT"));
    }

    @Test
    @DisplayName("POST /deposit on missing account returns 404 ACCOUNT_NOT_FOUND")
    void deposit_accountNotFound_returns404() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(depositMoneyUseCase.execute(any()))
            .thenThrow(new AccountNotFoundException(accountId));

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", accountId)
                .contentType(APPLICATION_JSON)
                .content("{\"amount\":50.00,\"currency\":\"BRL\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /deposit with exhausted retries returns 409 OPTIMISTIC_LOCK_CONFLICT")
    void deposit_exhaustedRetries_returns409() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(depositMoneyUseCase.execute(any()))
            .thenThrow(new OptimisticLockException(accountId));

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", accountId)
                .contentType(APPLICATION_JSON)
                .content("{\"amount\":50.00,\"currency\":\"BRL\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    // ---------------- POST /api/v1/accounts/{id}/withdraw ----------------

    @Test
    @DisplayName("POST /withdraw success returns 200 with reduced balance")
    void withdraw_success_returns200() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(withdrawMoneyUseCase.execute(any()))
            .thenReturn(new WithdrawMoneyResult(accountId, new BigDecimal("70.00"), "BRL"));

        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", accountId)
                .contentType(APPLICATION_JSON)
                .content("{\"amount\":30.00,\"currency\":\"BRL\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(70.00));
    }

    @Test
    @DisplayName("POST /withdraw on insufficient balance returns 422 INSUFFICIENT_FUNDS")
    void withdraw_insufficientBalance_returns422() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(withdrawMoneyUseCase.execute(any()))
            .thenThrow(new InsufficientFundsException(
                Money.of(new BigDecimal("100.00"), "BRL"),
                Money.of(new BigDecimal("10.00"), "BRL")));

        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", accountId)
                .contentType(APPLICATION_JSON)
                .content("{\"amount\":100.00,\"currency\":\"BRL\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    @DisplayName("POST /withdraw with missing amount returns 400 VALIDATION_ERROR")
    void withdraw_missingAmount_returns400() throws Exception {
        UUID accountId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", accountId)
                .contentType(APPLICATION_JSON)
                .content("{\"currency\":\"BRL\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /withdraw with zero amount returns 422 INVALID_AMOUNT")
    void withdraw_zeroAmount_returns422() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(withdrawMoneyUseCase.execute(any()))
            .thenThrow(new InvalidAmountException("amount must be positive, got: 0"));

        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", accountId)
                .contentType(APPLICATION_JSON)
                .content("{\"amount\":0,\"currency\":\"BRL\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("INVALID_AMOUNT"));
    }

    // ---------------- POST /api/v1/accounts/{id}/transfer ----------------

    @Test
    @DisplayName("POST /transfer success returns 200")
    void transfer_success_returns200() throws Exception {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        when(transferMoneyUseCase.execute(any()))
            .thenReturn(new TransferMoneyResult(source, target, new BigDecimal("25.00"), "BRL"));

        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                .contentType(APPLICATION_JSON)
                .content("{\"targetAccountId\":\"" + target + "\",\"amount\":25.00,\"currency\":\"BRL\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceAccountId").value(source.toString()))
            .andExpect(jsonPath("$.targetAccountId").value(target.toString()));
    }

    @Test
    @DisplayName("POST /transfer with self-transfer returns 422 SELF_TRANSFER_NOT_ALLOWED")
    void transfer_selfTransfer_returns422() throws Exception {
        UUID id = UUID.randomUUID();
        when(transferMoneyUseCase.execute(any()))
            .thenThrow(new SelfTransferNotAllowedException(id));

        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", id)
                .contentType(APPLICATION_JSON)
                .content("{\"targetAccountId\":\"" + id + "\",\"amount\":10.00,\"currency\":\"BRL\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("SELF_TRANSFER_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("POST /transfer with missing targetAccountId returns 400 VALIDATION_ERROR")
    void transfer_missingTargetId_returns400() throws Exception {
        UUID source = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                .contentType(APPLICATION_JSON)
                .content("{\"amount\":10.00,\"currency\":\"BRL\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /transfer on missing target account returns 404 ACCOUNT_NOT_FOUND")
    void transfer_targetNotFound_returns404() throws Exception {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        when(transferMoneyUseCase.execute(any()))
            .thenThrow(new AccountNotFoundException(target));

        mockMvc.perform(post("/api/v1/accounts/{id}/transfer", source)
                .contentType(APPLICATION_JSON)
                .content("{\"targetAccountId\":\"" + target + "\",\"amount\":10.00,\"currency\":\"BRL\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
    }
}
