package com.wesleytaumaturgo.ledgerflow.command.domain.model;

/**
 * Account lifecycle status (D-01).
 * MVP has a single value ACTIVE — Account.createAccount() always sets ACTIVE.
 * The status-change feature (toggle to INACTIVE) is post-MVP. The TransferMoneyUseCase
 * still validates source.isActive() AND target.isActive() so the validation path is
 * present from day one (D-02).
 */
public enum AccountStatus {
    ACTIVE
}
