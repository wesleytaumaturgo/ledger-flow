package com.wesleytaumaturgo.ledgerflow.command.application.testfixture;

import com.wesleytaumaturgo.ledgerflow.command.infrastructure.eventstore.PostgresEventStore;

/**
 * Fixture class for ArchUnit negative tests only.
 * Simulates an application-layer class that illegally imports from infrastructure.
 * Used to verify that the "application must not import infrastructure" rule detects violations.
 * MUST NOT be in production source — lives in test source tree only.
 */
public class InfrastructureUsingApplicationFixture {

    @SuppressWarnings("unused")
    private PostgresEventStore store;
}
