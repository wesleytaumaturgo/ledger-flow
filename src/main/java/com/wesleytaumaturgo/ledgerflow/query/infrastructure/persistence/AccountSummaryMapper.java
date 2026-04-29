package com.wesleytaumaturgo.ledgerflow.query.infrastructure.persistence;

import com.wesleytaumaturgo.ledgerflow.query.application.usecase.BalanceView;
import org.springframework.stereotype.Component;

/**
 * Translates AccountSummaryEntity (JPA) → BalanceView (domain DTO).
 * Pure transformation — no business logic, no state.
 */
@Component
public class AccountSummaryMapper {

    /**
     * Maps a JPA entity to an immutable BalanceView.
     *
     * @param entity the account summary entity
     * @return immutable balance view
     */
    public BalanceView toView(AccountSummaryEntity entity) {
        return new BalanceView(
                entity.getAccountId(),
                entity.getCurrentBalance(),
                entity.getCurrency(),
                entity.getTransactionCount(),
                entity.getLastTransactionAt()
        );
    }
}
