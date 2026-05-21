package co.istad.makara.account.application.dto.create;

import co.istad.makara.common.domain.valueobject.AccountId;
import co.istad.makara.common.domain.valueobject.Money;
import co.istad.makara.common.domain.valueobject.TransactionId;

public record CreateDepositRequest(
        AccountId accountId,
        TransactionId transactionId,
        Money amount,
        String remark
) {
}
