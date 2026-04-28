package com.wesleytaumaturgo.ledgerflow.command.infrastructure.eventstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry-based event deserializer.
 * Phase 2 registers concrete DomainEvent implementations via registerEventType().
 * This bean is a @Component so Spring manages its lifecycle.
 *
 * Design: Keeps domain layer pure — no @JsonTypeInfo on DomainEvent interface.
 * Infrastructure owns the type registry (D-02).
 */
@Component
public class DefaultEventDeserializer implements EventDeserializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventDeserializer.class);

    private final ObjectMapper objectMapper;
    private final Map<String, Class<? extends DomainEvent>> registry = new ConcurrentHashMap<>();

    public DefaultEventDeserializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Register a DomainEvent subtype for deserialization.
     * Called by Phase 2 configuration when concrete event classes are available.
     *
     * @param eventType the simple class name used as event_type in the database
     * @param clazz     the concrete DomainEvent implementation class
     */
    public void registerEventType(String eventType, Class<? extends DomainEvent> clazz) {
        registry.put(eventType, clazz);
        log.debug("Registered event type: {} -> {}", eventType, clazz.getSimpleName());
    }

    @Override
    public DomainEvent deserialize(String eventType, String eventDataJson,
                                   long sequenceNumber, Instant occurredAt) {
        Class<? extends DomainEvent> clazz = registry.get(eventType);
        if (clazz == null) {
            throw new EventDeserializationException(
                "Unknown event type: '" + eventType + "'. " +
                "Register it via DefaultEventDeserializer.registerEventType() in Phase 2 configuration.");
        }
        try {
            return objectMapper.readValue(eventDataJson, clazz);
        } catch (Exception e) {
            throw new EventDeserializationException(
                "Failed to deserialize event of type '" + eventType + "': " + e.getMessage(), e);
        }
    }
}
