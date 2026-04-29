package com.wesleytaumaturgo.ledgerflow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Testcontainers base for all integration tests.
 *
 * Container lifecycle: The static @Container field starts a fresh PostgreSQL container
 * for each IT class and stops it when that class finishes. @DirtiesContext ensures
 * Spring's test context cache is invalidated between classes so the next class receives
 * the correct datasource URL via @DynamicPropertySource — preventing stale connections
 * to a stopped container when multiple *IT.java classes run in the same JVM.
 *
 * All integration test classes (*IT.java) MUST extend this base.
 * H2, HSQLDB, and Derby are forbidden as test datasources (see pom.xml).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public abstract class IntegrationTestBase {

    // static = one container per JVM process, shared across ALL subclasses
    // non-static = one container per test class (30s startup penalty each)
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("ledgerflow_test")
                    .withUsername("testuser")
                    .withPassword("testpass");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate restTemplate;
}
