package co.istad.makara.common.domain.valueobject;

import co.istad.makara.common.domain.exception.DomainException;

import java.math.BigDecimal;

public record Money(
        BigDecimal amount,
        Currency currency
) {

    public boolean isLessThan(Money otherAmount) {
        checkSameCurrency(otherAmount.currency);
        return this.amount.compareTo(otherAmount.amount) < 0;
    }

    public boolean isLessThanOrEqual(Money otherAmount) {
        checkSameCurrency(otherAmount.currency);
        return this.amount.compareTo(otherAmount.amount) <= 0;
    }

    public boolean isGreaterThan(Money otherAmount) {
        checkSameCurrency(otherAmount.currency);
        return this.amount.compareTo(otherAmount.amount) > 0;
    }

    public boolean isGreaterThanOrEqual(Money otherAmount) {
        checkSameCurrency(otherAmount.currency);
        return this.amount.compareTo(otherAmount.amount) >= 0;
    }

    public void checkSameCurrency(Currency otherCurrency) {
        if (this.currency != otherCurrency) {
            throw new DomainException("Currency doesn't match");
        }
    }
    public Money add(Money otherAmount) {
        if (this.currency != otherAmount.currency) {
            throw new DomainException("Currency mismatch");
        }
        return new Money(this.amount.add(otherAmount.amount), this.currency);
    }
    public Money subtract(Money otherAmount) {
        if (this.currency != otherAmount.currency) {
            throw new DomainException("Currency mismatch");
        }
        return new Money(this.amount.subtract(otherAmount.amount), this.currency);
    }





}
