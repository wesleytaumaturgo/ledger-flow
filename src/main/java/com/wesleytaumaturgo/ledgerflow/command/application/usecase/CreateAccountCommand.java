package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import java.util.Objects;

/**
 * Command for creating a new Account. ownerId comes from the controller's request DTO
 * after Bean Validation has confirmed @NotBlank.
 */
public record CreateAccountCommand(String ownerId) {
    public CreateAccountCommand {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
    }
}
