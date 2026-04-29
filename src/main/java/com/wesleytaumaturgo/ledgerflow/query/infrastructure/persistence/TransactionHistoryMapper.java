package com.wesleytaumaturgo.ledgerflow.query.infrastructure.persistence;

import com.wesleytaumaturgo.ledgerflow.query.application.usecase.TransactionHistoryView;
import org.springframework.stereotype.Component;

/**
 * Translates TransactionHistoryEntity (JPA) → TransactionHistoryView (domain DTO).
 * Pure transformation — no business logic, no state.
 */
@Component
public class TransactionHistoryMapper {

    /**
     * Maps a JPA entity to an immutable TransactionHistoryView.
     *
     * @param entity the transaction history entity
     * @return immutable transaction history view
     */
    public TransactionHistoryView toView(TransactionHistoryEntity entity) {
        return new TransactionHistoryView(
                entity.getId(),
                entity.getAccountId(),
                entity.getEventType(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getDescription(),
                entity.getOccurredAt(),
                entity.getCounterpartyAccountId()
        );
    }
}
