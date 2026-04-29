package com.wesleytaumaturgo.ledgerflow.command.domain.model;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.InvalidAmountException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Money value object — BigDecimal with scale 2 and HALF_EVEN rounding.
 * Final class (not record): factory methods of() and zero() differ in validation;
 * scale defensively re-applied in private constructor.
 *
 * ArchUnit Rule 6 forbids double/float in this package — use BigDecimal exclusively.
 */
public final class Money {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final BigDecimal amount;
    private final String currency;   // ISO 4217, e.g. "BRL"

    private Money(BigDecimal amount, String currency) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        this.amount = amount.setScale(SCALE, ROUNDING);
        this.currency = currency;
    }

    /**
     * Factory for valid (positive) amounts. Throws InvalidAmountException for
     * null, zero, or negative values. Used by deposit/withdraw/transfer paths.
     */
    public static Money of(BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("amount must be positive, got: " + amount);
        }
        return new Money(amount, currency);
    }

    /**
     * Factory for zero-balance initialization. Does NOT throw.
     * Used inside Account.applyAccountCreated() to seed initial balance.
     */
    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public boolean isLessThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    public boolean isZeroOrNegative() {
        return this.amount.compareTo(BigDecimal.ZERO) <= 0;
    }

    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    private void assertSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Currency mismatch: " + this.currency + " vs " + other.currency);
        }
    }

    public BigDecimal amount()  { return amount; }
    public String currency()    { return currency; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return amount.compareTo(other.amount) == 0 && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
