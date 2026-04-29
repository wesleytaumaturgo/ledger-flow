package com.wesleytaumaturgo.ledgerflow.api;

import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.CreateAccountResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.DepositMoneyResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.TransferMoneyRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.TransferMoneyResponse;
import com.wesleytaumaturgo.ledgerflow.api.dto.WithdrawMoneyRequest;
import com.wesleytaumaturgo.ledgerflow.api.dto.WithdrawMoneyResponse;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.CreateAccountCommand;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.CreateAccountResult;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.CreateAccountUseCase;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.DepositMoneyCommand;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.DepositMoneyResult;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.DepositMoneyUseCase;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.TransferMoneyCommand;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.TransferMoneyResult;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.TransferMoneyUseCase;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.WithdrawMoneyCommand;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.WithdrawMoneyResult;
import com.wesleytaumaturgo.ledgerflow.command.application.usecase.WithdrawMoneyUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * HTTP adapter for command-side operations on the Account aggregate.
 *
 * Thin controller (CLAUDE.md §3.1, .claude/rules/controllers.md):
 *   - Zero business logic — orchestration lives in use cases
 *   - Zero @Transactional — transactions belong to use cases (ArchUnit Rule 2)
 *   - Zero @ExceptionHandler — handled by GlobalExceptionHandler (ArchUnit Rule 3)
 *   - Zero imports from command/domain/** (ArchUnit Rule 5) — uses application.usecase types only
 *   - @Valid on every @RequestBody — Bean Validation triggers 400 with VALIDATION_ERROR
 *
 * REQ-account-create / FR-001, REQ-money-deposit / FR-002, REQ-money-withdraw / FR-003,
 * REQ-money-transfer / FR-004 — all four endpoints exposed here.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public final class AccountCommandController {

    private final CreateAccountUseCase createAccountUseCase;
    private final DepositMoneyUseCase  depositMoneyUseCase;
    private final WithdrawMoneyUseCase withdrawMoneyUseCase;
    private final TransferMoneyUseCase transferMoneyUseCase;

    public AccountCommandController(CreateAccountUseCase createAccountUseCase,
                                    DepositMoneyUseCase depositMoneyUseCase,
                                    WithdrawMoneyUseCase withdrawMoneyUseCase,
                                    TransferMoneyUseCase transferMoneyUseCase) {
        this.createAccountUseCase = createAccountUseCase;
        this.depositMoneyUseCase  = depositMoneyUseCase;
        this.withdrawMoneyUseCase = withdrawMoneyUseCase;
        this.transferMoneyUseCase = transferMoneyUseCase;
    }

    @PostMapping
    public ResponseEntity<CreateAccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        CreateAccountResult result = createAccountUseCase.execute(
            new CreateAccountCommand(request.ownerId()));
        URI location = URI.create("/api/v1/accounts/" + result.accountId());
        return ResponseEntity.created(location)
            .body(new CreateAccountResponse(result.accountId()));
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<DepositMoneyResponse> deposit(
            @PathVariable UUID id,
            @Valid @RequestBody DepositMoneyRequest request) {
        DepositMoneyResult result = depositMoneyUseCase.execute(
            new DepositMoneyCommand(id, request.amount(), request.currency()));
        return ResponseEntity.ok(new DepositMoneyResponse(
            result.accountId(), result.balance(), result.currency()));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<WithdrawMoneyResponse> withdraw(
            @PathVariable UUID id,
            @Valid @RequestBody WithdrawMoneyRequest request) {
        WithdrawMoneyResult result = withdrawMoneyUseCase.execute(
            new WithdrawMoneyCommand(id, request.amount(), request.currency()));
        return ResponseEntity.ok(new WithdrawMoneyResponse(
            result.accountId(), result.balance(), result.currency()));
    }

    @PostMapping("/{id}/transfer")
    public ResponseEntity<TransferMoneyResponse> transfer(
            @PathVariable UUID id,
            @Valid @RequestBody TransferMoneyRequest request) {
        TransferMoneyResult result = transferMoneyUseCase.execute(
            new TransferMoneyCommand(id, request.targetAccountId(),
                request.amount(), request.currency()));
        return ResponseEntity.ok(new TransferMoneyResponse(
            result.sourceAccountId(), result.targetAccountId(),
            result.amount(), result.currency()));
    }
}
