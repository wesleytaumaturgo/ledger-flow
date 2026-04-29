package com.wesleytaumaturgo.ledgerflow.command.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables binding of {@link CommandProperties} from application.yml.
 * Lives in command/infrastructure/config — infrastructure layer, OK to use Spring annotations.
 */
@Configuration
@EnableConfigurationProperties(CommandProperties.class)
public class CommandConfig {
    // No beans here — @EnableConfigurationProperties wires CommandProperties as a bean
}
