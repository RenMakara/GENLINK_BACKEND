package co.istad.makara.account.domain.validate;

import co.istad.makara.account.domain.exception.AccountDomainException;

public class AccountValidation {

    public static void validateAccountNumber(String accountNumber) {
        if (accountNumber == null) {
            throw new AccountDomainException("Account must be not null");
        }
        if (!accountNumber.matches("^\\d{9}$")) {
            throw new AccountDomainException("Invalid account number");
        }
    }
}
