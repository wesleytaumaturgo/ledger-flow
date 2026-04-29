package com.wesleytaumaturgo.ledgerflow.query.infrastructure.persistence;

import com.wesleytaumaturgo.ledgerflow.query.application.usecase.BalanceView;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.AccountSummaryData;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.AccountSummaryRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Infrastructure adapter implementing the domain AccountSummaryRepository port.
 * Bridges Spring Data JPA to the pure-Java domain interface.
 * No @Transactional — participates in the caller's (use case/projector) transaction.
 */
@Repository
class AccountSummaryRepositoryAdapter implements AccountSummaryRepository {

    private final AccountSummaryJpaRepository jpaRepository;
    private final AccountSummaryMapper mapper;

    AccountSummaryRepositoryAdapter(AccountSummaryJpaRepository jpaRepository,
                                     AccountSummaryMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<BalanceView> findById(UUID accountId) {
        return jpaRepository.findById(accountId)
                .map(mapper::toView);
    }

    @Override
    public void save(AccountSummaryData data) {
        AccountSummaryEntity entity = jpaRepository.findById(data.accountId())
                .orElseGet(AccountSummaryEntity::new);

        entity.setAccountId(data.accountId());
        entity.setOwnerId(data.ownerId());
        entity.setCurrentBalance(data.currentBalance());
        entity.setCurrency(data.currency());
        entity.setTotalDeposited(data.totalDeposited());
        entity.setTotalWithdrawn(data.totalWithdrawn());
        entity.setTransactionCount(data.transactionCount());
        entity.setLastEventSequence(data.lastEventSequence());
        entity.setLastTransactionAt(data.lastTransactionAt());

        jpaRepository.save(entity);
    }

    @Override
    public void deleteById(UUID accountId) {
        jpaRepository.deleteById(accountId);
    }
}
