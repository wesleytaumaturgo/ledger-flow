package com.wesleytaumaturgo.ledgerflow.query.application.usecase;

import java.time.Instant;

/**
 * Immutable filter criteria for transaction history queries.
 * All fields are nullable — null means "no filter applied" for that dimension.
 * No framework imports — pure Java record.
 */
public record TransactionFilter(
        Instant from,
        Instant to,
        String eventType
) {

    /**
     * Factory for an empty filter — returns all transactions without restriction.
     */
    public static TransactionFilter noFilter() {
        return new TransactionFilter(null, null, null);
    }
}
