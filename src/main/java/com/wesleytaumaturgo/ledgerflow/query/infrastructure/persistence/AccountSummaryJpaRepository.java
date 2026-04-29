package com.wesleytaumaturgo.ledgerflow.query.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the account_summary read model.
 * Standard CRUD — no custom queries needed; all fields are flat (no joins).
 */
interface AccountSummaryJpaRepository extends JpaRepository<AccountSummaryEntity, UUID> {
    // Standard findById, save, deleteById provided by JpaRepository
}
