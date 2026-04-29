package com.wesleytaumaturgo.ledgerflow.query.application.projectors;

import java.util.UUID;

/**
 * Result of a projector rebuild operation.
 * Returned by AccountProjector.rebuild() to report how many events were replayed.
 */
public record RebuildResult(UUID accountId, int rebuiltEvents) {}
