package com.wesleytaumaturgo.ledgerflow.command.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed identifier for the Account aggregate.
 * Record VO: 1 field, simple null check, no behavior beyond identity.
 */
public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "AccountId value must not be null");
    }

    public static AccountId generate() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
