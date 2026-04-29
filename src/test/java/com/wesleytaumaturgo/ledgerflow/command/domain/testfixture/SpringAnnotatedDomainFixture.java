package com.wesleytaumaturgo.ledgerflow.command.domain.testfixture;

import org.springframework.stereotype.Service;

/**
 * Fixture class for ArchUnit negative tests only.
 * Simulates a domain class that illegally uses a Spring annotation.
 * Used to verify that the "domain must not use Spring" rule detects violations.
 * MUST NOT be in production source — lives in test source tree only.
 */
@Service
public class SpringAnnotatedDomainFixture {
}
