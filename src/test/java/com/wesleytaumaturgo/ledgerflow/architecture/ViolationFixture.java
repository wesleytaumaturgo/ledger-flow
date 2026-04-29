package com.wesleytaumaturgo.ledgerflow.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.wesleytaumaturgo.ledgerflow.command.application.testfixture.InfrastructureUsingApplicationFixture;
import com.wesleytaumaturgo.ledgerflow.command.domain.testfixture.SpringAnnotatedDomainFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Negative tests proving that ArchUnit rules actually detect violations.
 * Each test imports a fixture class that intentionally violates a rule,
 * then asserts the rule throws AssertionError.
 *
 * Without these negative tests a rule using allowEmptyShould(false) might scan
 * an empty classpath and pass vacuously — this suite proves rules have teeth.
 */
class ViolationFixture {

    @Test
    @DisplayName("Rule 1 negative: domain must not use Spring — detects @Service violation")
    void domainMustNotUseSpring_detectsViolation() {
        JavaClasses violating = new ClassFileImporter()
            .importClasses(SpringAnnotatedDomainFixture.class);

        ArchRule domainMustNotUseSpringAnnotations = noClasses()
            .that().resideInAPackage("..command.domain..")
            .should().beAnnotatedWith("org.springframework.stereotype.Service")
            .allowEmptyShould(false)
            .because("Domain layer must be pure Java — no Spring annotations");

        assertThatThrownBy(() -> domainMustNotUseSpringAnnotations.check(violating))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("Rule 4 negative: domain must not depend on infrastructure — detects import violation")
    void applicationMustNotImportInfrastructure_detectsViolation() {
        JavaClasses violating = new ClassFileImporter()
            .importClasses(InfrastructureUsingApplicationFixture.class);

        ArchRule applicationMustNotImportInfrastructure = noClasses()
            .that().resideInAPackage("..command.application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(false)
            .because("Application layer must not depend on infrastructure implementations");

        assertThatThrownBy(() -> applicationMustNotImportInfrastructure.check(violating))
            .isInstanceOf(AssertionError.class);
    }
}
