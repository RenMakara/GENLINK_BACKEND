package co.istad.makara.account.domain.event;

import co.istad.makara.common.domain.valueobject.AccountId;
import co.istad.makara.common.domain.valueobject.CustomerId;
import co.istad.makara.common.domain.valueobject.Money;
import co.istad.makara.common.domain.valueobject.TransactionId;
import lombok.Builder;

import java.time.ZonedDateTime;

@Builder
public record MoneyWithdrawnEvent(
        AccountId accountId,
        CustomerId customerId,
        TransactionId transactionId,
        Money amount,
        Money newBalance,
        String remark,
        ZonedDateTime createdAt
) {

}
