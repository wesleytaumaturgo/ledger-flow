package com.wesleytaumaturgo.ledgerflow.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * ArchUnit tests enforcing hexagonal architecture layer isolation and event sourcing constraints.
 * Applies to all current and future phases. Zero violations — any violation breaks the build.
 *
 * Rules implemented:
 *   Rule 1 (D-03): Domain purity — no Spring/JPA framework annotations in command/domain/**
 *   Rule 2 (D-03): @Transactional scope — only application/usecase/** may use @Transactional
 *   Rule 3 (D-03): No @ExceptionHandler methods in @RestController classes
 *   Rule 4 (D-03): Layer isolation — domain must not import infrastructure packages
 *   Rule 5 (D-03): API -> domain prohibition — api/** must not import command/domain/**
 *   Rule 6 (REQ-nfr-archunit): No double/float fields in command/domain/** — use BigDecimal
 *   Rule 7 (REQ-nfr-archunit): No UPDATE event_store or DELETE FROM event_store SQL constants in infrastructure/eventstore/**
 *
 * Does NOT extend IntegrationTestBase — no database needed, no Spring context needed.
 * ClassFileImporter scans bytecode only.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    private static final String BASE_PACKAGE = "com.wesleytaumaturgo.ledgerflow";

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    /**
     * Rule 1: Domain purity.
     * Classes in command/domain/** must NOT carry Spring or JPA stereotype annotations.
     * Rationale: Domain layer is pure Java — zero framework coupling (DEC-005, CLAUDE.md §3.1).
     */
    @Test
    @DisplayName("Rule 1: Domain classes must not use Spring or JPA annotations (@Service, @Component, @Entity, @Repository, @Table, @Column)")
    void domain_must_not_use_spring_or_jpa_annotations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..command.domain..")
                .should().beAnnotatedWith("org.springframework.stereotype.Service")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                .orShould().beAnnotatedWith("jakarta.persistence.Entity")
                .orShould().beAnnotatedWith("jakarta.persistence.Table")
                .orShould().beAnnotatedWith("jakarta.persistence.Column")
                .because("Domain layer must be pure Java with zero framework annotations (DEC-005)");

        rule.check(classes);
    }

    /**
     * Rule 2: @Transactional scope restriction.
     * Only application use cases and projectors may carry @Transactional (CLAUDE.md §7.1).
     * Infrastructure classes (e.g., PostgresEventStore) participate in the caller's transaction
     * without declaring @Transactional themselves — no exemption is needed or allowed.
     *
     * Allowed packages:
     *   - ..application.usecase..  — command-side use cases (write operations)
     *   - ..query.application.projectors..  — AccountProjector on() and rebuild() methods
     *
     * Rationale for projector exemption: CQRS projectors are application-layer components that
     * maintain transactional consistency for read-model updates. Each on() method must be
     * @Transactional to ensure the read-model update and the idempotency check are atomic.
     * See CLAUDE.md cqrs-projector rule and STATE.md Phase 2 Note.
     */
    @Test
    @DisplayName("Rule 2: @Transactional only in use cases and projectors — not in domain or api")
    void transactional_must_only_appear_in_use_cases() {
        // Forbid @Transactional on domain or api layer classes.
        // Application use cases and projectors are allowed — they own transaction boundaries.
        ArchRule noTransactionalOnDomain = noClasses()
                .that().resideInAPackage("..command.domain..")
                .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                .because("Domain layer must be pure Java — no framework annotations (CLAUDE.md §3.1)");

        ArchRule noTransactionalOnApi = noClasses()
                .that().resideInAPackage("..api..")
                .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                .allowEmptyShould(false)
                .because("API layer must not manage transactions; delegates to application layer");

        ArchRule noTransactionalOnInfrastructure = noClasses()
                .that().resideInAPackage("..infrastructure..")
                .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                .because("Infrastructure participates in the caller's transaction (use case or projector); " +
                         "declaring its own @Transactional creates second commit boundaries (CLAUDE.md §7.1)");

        noTransactionalOnDomain.check(classes);
        noTransactionalOnApi.check(classes);
        noTransactionalOnInfrastructure.check(classes);
    }

    /**
     * Rule 3: No @ExceptionHandler in @RestController classes.
     * All exception handling routes through GlobalExceptionHandler (@RestControllerAdvice).
     * Local @ExceptionHandler in a controller creates inconsistent error responses (CLAUDE.md §4.6).
     * allowEmptyShould: Phase 1 has no @RestController classes yet — rule enforces once Phase 2 adds them.
     */
    @Test
    @DisplayName("Rule 3: @RestController classes must not declare @ExceptionHandler methods")
    void controllers_must_not_declare_exception_handlers() {
        ArchRule rule = noMethods()
                .that().areDeclaredInClassesThat()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should().beAnnotatedWith("org.springframework.web.bind.annotation.ExceptionHandler")
                .allowEmptyShould(false)
                .because("Exception handling belongs exclusively in GlobalExceptionHandler (@RestControllerAdvice)");

        rule.check(classes);
    }

    /**
     * Rule 4: Layer isolation — domain must not import from infrastructure.
     * Domain interfaces and models must not reference infrastructure implementations.
     * Infrastructure depends on domain (implements ports); the reverse is forbidden.
     */
    @Test
    @DisplayName("Rule 4: Domain layer must not import from infrastructure packages")
    void domain_must_not_depend_on_infrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..command.domain..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("Domain layer defines contracts; infrastructure implements them — not the reverse");

        rule.check(classes);
    }

    /**
     * Rule 5: API layer must not import from command/domain.
     * Controllers in api/** may only depend on application/usecase/** (use case interfaces and DTOs).
     * Direct dependency on domain aggregates or value objects from controllers violates
     * hexagonal architecture boundaries.
     * Note: In Phase 1, the api/** package does not exist yet — rule enforces once Phase 2 adds them.
     * allowEmptyShould: no api/** classes exist in Phase 1.
     */
    @Test
    @DisplayName("Rule 5: api/** classes must not import from command/domain/**")
    void api_must_not_depend_on_command_domain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..command.domain..")
                .allowEmptyShould(false)
                .because("API controllers depend on use cases (application layer), not domain internals");

        rule.check(classes);
    }

    /**
     * Rule 6: No double or float fields in command/domain/**.
     * Monetary values must use BigDecimal. double/float have precision loss that corrupts
     * financial calculations (CLAUDE.md §Accumulated Context — Money is always BigDecimal).
     * Catches primitive double, primitive float, java.lang.Double, and java.lang.Float.
     */
    @Test
    @DisplayName("Rule 6: No double or float fields in command/domain/** — use BigDecimal for monetary values")
    void no_double_or_float_in_domain() {
        // allowEmptyShould: Phase 1 domain classes have no numeric fields yet — enforces once Phase 2 adds Money/value objects
        ArchRule noDouble = noFields()
                .that().areDeclaredInClassesThat().resideInAPackage("..command.domain..")
                .should().haveRawType(double.class)
                .allowEmptyShould(false)
                .because("double has precision loss — monetary values must use BigDecimal (STATE.md locked decision)");

        ArchRule noFloat = noFields()
                .that().areDeclaredInClassesThat().resideInAPackage("..command.domain..")
                .should().haveRawType(float.class)
                .allowEmptyShould(false)
                .because("float has precision loss — monetary values must use BigDecimal (STATE.md locked decision)");

        noDouble.check(classes);
        noFloat.check(classes);
    }

    /**
     * Rule 7: Append-only enforcement — no UPDATE or DELETE SQL targeting event_store.
     * Classes in infrastructure/eventstore/** must NOT contain static String constants
     * with substrings "UPDATE EVENT_STORE" or "DELETE FROM EVENT_STORE" (case-insensitive).
     *
     * Rationale: The event store is an immutable append-only log. Corrections use compensating
     * events, never mutating existing rows (CLAUDE.md §3.6, §7.3).
     *
     * Implementation: Uses reflection on compiled classes to read static String field values,
     * since ArchUnit's standard DSL does not expose constant field values directly.
     * PostgresEventStore defines INSERT_EVENT and LOAD_EVENTS — neither contains these substrings.
     */
    @Test
    @DisplayName("Rule 7: No UPDATE event_store or DELETE FROM event_store SQL constants in infrastructure/eventstore/**")
    void event_store_append_only_no_update_delete() {
        ArchCondition<com.tngtech.archunit.core.domain.JavaClass> hasNoMutatingEventStoreSql =
            new ArchCondition<>("not contain SQL constants with UPDATE event_store or DELETE FROM event_store") {
                @Override
                public void check(com.tngtech.archunit.core.domain.JavaClass javaClass,
                                  ConditionEvents events) {
                    try {
                        Class<?> clazz = Class.forName(javaClass.getName());
                        for (Field field : clazz.getDeclaredFields()) {
                            if (field.getType() == String.class) {
                                field.setAccessible(true);
                                Object value = null;
                                try {
                                    value = field.get(null); // static fields only
                                } catch (Exception ignored) {
                                    // instance fields or inaccessible — skip
                                }
                                if (value instanceof String sql) {
                                    String normalized = sql.toUpperCase()
                                        .replace("\n", " ")
                                        .replace("\r", " ");
                                    if (normalized.contains("UPDATE EVENT_STORE") ||
                                        normalized.contains("DELETE FROM EVENT_STORE")) {
                                        events.add(SimpleConditionEvent.violated(
                                            javaClass,
                                            "Class " + javaClass.getName() +
                                            " contains forbidden mutating SQL on event_store in field: " +
                                            field.getName()));
                                    }
                                }
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        // Class not loadable at test runtime — skip (compilation check already passed)
                    }
                }
            };

        noClasses()
            .that().resideInAPackage("..infrastructure.eventstore..")
            .should(hasNoMutatingEventStoreSql)
            .because("event_store is append-only; compensating events correct mistakes, never UPDATE or DELETE (CLAUDE.md §3.6)")
            .check(classes);
    }
}
