package com.wesleytaumaturgo.ledgerflow.query.infrastructure.persistence;

import com.wesleytaumaturgo.ledgerflow.query.application.usecase.TransactionFilter;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.TransactionHistoryView;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.TransactionHistoryData;
import com.wesleytaumaturgo.ledgerflow.query.domain.model.TransactionHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Infrastructure adapter implementing the domain TransactionHistoryRepository port.
 * Bridges Spring Data JPA to the pure-Java domain interface.
 * No @Transactional — participates in the caller's (use case/projector) transaction.
 *
 * Pagination: domain interface uses (page, size) primitives; adapter converts to
 * Spring Data PageRequest. Max size is enforced by the query use case layer.
 */
@Repository
class TransactionHistoryRepositoryAdapter implements TransactionHistoryRepository {

    private final TransactionHistoryJpaRepository jpaRepository;
    private final TransactionHistoryMapper mapper;

    TransactionHistoryRepositoryAdapter(TransactionHistoryJpaRepository jpaRepository,
                                         TransactionHistoryMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public List<TransactionHistoryView> findByAccountId(
            UUID accountId,
            TransactionFilter filter,
            int page,
            int size) {

        Page<TransactionHistoryEntity> resultPage = jpaRepository.findByAccountIdWithFilters(
                accountId,
                filter.eventType(),
                filter.from(),
                filter.to(),
                PageRequest.of(page, size));

        return resultPage.getContent().stream()
                .map(mapper::toView)
                .toList();
    }

    @Override
    public long countByAccountId(UUID accountId, TransactionFilter filter) {
        return jpaRepository.countByAccountIdWithFilters(
                accountId,
                filter.eventType(),
                filter.from(),
                filter.to());
    }

    @Override
    public void save(TransactionHistoryData data) {
        TransactionHistoryEntity entity = new TransactionHistoryEntity();
        entity.setAccountId(data.accountId());
        entity.setEventType(data.eventType());
        entity.setAmount(data.amount());
        entity.setCurrency(data.currency());
        entity.setDescription(data.description());
        entity.setOccurredAt(data.occurredAt());
        entity.setCounterpartyAccountId(data.counterpartyAccountId());
        entity.setSequenceNumber(data.sequenceNumber());
        jpaRepository.save(entity);
    }

    @Override
    public void deleteByAccountId(UUID accountId) {
        jpaRepository.deleteByAccountId(accountId);
    }
}
