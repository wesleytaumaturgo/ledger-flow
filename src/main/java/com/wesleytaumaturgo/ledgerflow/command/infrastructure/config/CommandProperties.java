package com.wesleytaumaturgo.ledgerflow.command.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Command-side configuration properties bound to application.yml {@code ledger.command.*} (D-03).
 *
 * @param maxRetries upper bound for OptimisticLockException retry loop in deposit / withdraw / transfer use cases.
 *                   CreateAccountUseCase has no retry (sequence 1 never conflicts — D-04).
 *                   Default value applied if property absent: 3.
 */
@ConfigurationProperties(prefix = "ledger.command")
public record CommandProperties(int maxRetries) {

    public CommandProperties {
        if (maxRetries < 1) {
            throw new IllegalArgumentException(
                "ledger.command.max-retries must be >= 1, got: " + maxRetries);
        }
    }
}
