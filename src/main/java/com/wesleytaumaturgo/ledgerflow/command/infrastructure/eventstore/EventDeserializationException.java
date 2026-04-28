package com.wesleytaumaturgo.ledgerflow.command.infrastructure.eventstore;

/**
 * Thrown when an event cannot be deserialized from the event store.
 * This is an infrastructure exception — logged at ERROR level, not domain level.
 */
public class EventDeserializationException extends RuntimeException {

    public EventDeserializationException(String message) {
        super(message);
    }

    public EventDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
