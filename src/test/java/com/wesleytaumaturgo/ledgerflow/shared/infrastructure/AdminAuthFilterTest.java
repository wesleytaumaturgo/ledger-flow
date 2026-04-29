package com.wesleytaumaturgo.ledgerflow.shared.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthFilterTest {

    @Test
    void keysMatch_nullProvided_returnsFalse() {
        assertThat(AdminAuthFilter.keysMatch(null, "secret")).isFalse();
    }

    @Test
    void keysMatch_wrongKey_returnsFalse() {
        assertThat(AdminAuthFilter.keysMatch("wrong-key", "secret")).isFalse();
    }

    @Test
    void keysMatch_emptyProvided_returnsFalse() {
        assertThat(AdminAuthFilter.keysMatch("", "secret")).isFalse();
    }

    @Test
    void keysMatch_correctKey_returnsTrue() {
        assertThat(AdminAuthFilter.keysMatch("secret", "secret")).isTrue();
    }

    @Test
    void keysMatch_prefixOfCorrectKey_returnsFalse() {
        assertThat(AdminAuthFilter.keysMatch("secre", "secret")).isFalse();
    }
}
