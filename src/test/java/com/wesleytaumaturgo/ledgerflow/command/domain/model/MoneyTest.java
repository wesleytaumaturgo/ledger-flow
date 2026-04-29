package com.wesleytaumaturgo.ledgerflow.command.domain.model;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.InvalidAmountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the Money value object. No Spring context, no database.
 * Verifies factory validation (positive/zero/negative/null), arithmetic, currency-mismatch
 * defense, and scale-insensitive equality / hashCode contract.
 */
class MoneyTest {

    private static final String BRL = "BRL";

    // ---------------- Factory: Money.of ----------------

    @Test
    @DisplayName("Money.of with positive amount succeeds and applies scale 2 with HALF_EVEN rounding")
    void of_positiveAmount_succeeds_andAppliesScale2() {
        Money money = Money.of(new BigDecimal("100.005"), BRL);

        // HALF_EVEN rounds 100.005 — the digit before 5 is 0 (even), so it rounds down to 100.00
        assertThat(money.amount()).isEqualByComparingTo("100.00");
        assertThat(money.amount().scale()).isEqualTo(2);
        assertThat(money.currency()).isEqualTo(BRL);
    }

    @Test
    @DisplayName("Money.of with null amount throws InvalidAmountException")
    void of_nullAmount_throwsInvalidAmountException() {
        assertThatThrownBy(() -> Money.of(null, BRL))
            .isInstanceOf(InvalidAmountException.class)
            .hasMessageContaining("must be positive");
    }

    @Test
    @DisplayName("Money.of with zero amount throws InvalidAmountException")
    void of_zeroAmount_throwsInvalidAmountException() {
        assertThatThrownBy(() -> Money.of(BigDecimal.ZERO, BRL))
            .isInstanceOf(InvalidAmountException.class)
            .hasMessageContaining("must be positive");
    }

    @Test
    @DisplayName("Money.of with negative amount throws InvalidAmountException")
    void of_negativeAmount_throwsInvalidAmountException() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-0.01"), BRL))
            .isInstanceOf(InvalidAmountException.class)
            .hasMessageContaining("must be positive");
    }

    // ---------------- Factory: Money.zero ----------------

    @Test
    @DisplayName("Money.zero produces zero balance and does NOT throw")
    void zero_producesZeroBalance_doesNotThrow() {
        Money zero = Money.zero(BRL);

        assertThat(zero.amount()).isEqualByComparingTo("0.00");
        assertThat(zero.currency()).isEqualTo(BRL);
        assertThat(zero.isZeroOrNegative()).isTrue();
        assertThat(zero.isPositive()).isFalse();
    }

    // ---------------- Arithmetic ----------------

    @Test
    @DisplayName("add returns sum of two Money instances with same currency")
    void add_sameCurrency_returnsSum() {
        Money a = Money.of(new BigDecimal("100.00"), BRL);
        Money b = Money.of(new BigDecimal("50.50"), BRL);

        Money result = a.add(b);

        assertThat(result.amount()).isEqualByComparingTo("150.50");
        assertThat(result.currency()).isEqualTo(BRL);
    }

    @Test
    @DisplayName("subtract returns difference with same currency")
    void subtract_sameCurrency_returnsDifference() {
        Money a = Money.of(new BigDecimal("100.00"), BRL);
        Money b = Money.of(new BigDecimal("30.00"), BRL);

        Money result = a.subtract(b);

        assertThat(result.amount()).isEqualByComparingTo("70.00");
        assertThat(result.currency()).isEqualTo(BRL);
    }

    @Test
    @DisplayName("add with different currency throws IllegalArgumentException with currency mismatch message")
    void add_differentCurrency_throwsIllegalArgumentException() {
        Money brl = Money.of(new BigDecimal("100.00"), BRL);
        Money usd = Money.of(new BigDecimal("100.00"), "USD");

        assertThatThrownBy(() -> brl.add(usd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Currency mismatch");
    }

    @Test
    @DisplayName("isLessThan returns true when this is strictly less than other; false otherwise")
    void isLessThan_strictlyLess_returnsTrueOrFalse() {
        Money fifty = Money.of(new BigDecimal("50.00"), BRL);
        Money hundred = Money.of(new BigDecimal("100.00"), BRL);

        assertThat(fifty.isLessThan(hundred)).isTrue();
        assertThat(hundred.isLessThan(fifty)).isFalse();
        assertThat(fifty.isLessThan(fifty)).isFalse();
    }

    @Test
    @DisplayName("isZeroOrNegative returns false for a positive Money")
    void isZeroOrNegative_positiveMoney_returnsFalse() {
        Money positive = Money.of(new BigDecimal("0.01"), BRL);

        assertThat(positive.isZeroOrNegative()).isFalse();
        assertThat(positive.isPositive()).isTrue();
    }

    // ---------------- Equality ----------------

    @Test
    @DisplayName("equals is scale-insensitive: 100.00 equals 100.000")
    void equals_isScaleInsensitive() {
        Money a = Money.of(new BigDecimal("100.00"), BRL);
        // Money constructor applies setScale(2), so 100.000 becomes 100.00 internally
        Money b = Money.of(new BigDecimal("100.000"), BRL);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("equals returns false for same amount but different currency")
    void equals_differentCurrency_returnsFalse() {
        Money brl = Money.of(new BigDecimal("100.00"), BRL);
        Money usd = Money.of(new BigDecimal("100.00"), "USD");

        assertThat(brl).isNotEqualTo(usd);
    }
}
