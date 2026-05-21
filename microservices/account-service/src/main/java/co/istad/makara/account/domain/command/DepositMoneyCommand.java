package co.istad.makara.account.domain.command;

import co.istad.makara.common.domain.valueobject.AccountId;
import co.istad.makara.common.domain.valueobject.Money;
import co.istad.makara.common.domain.valueobject.TransactionId;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record DepositMoneyCommand(
        @TargetAggregateIdentifier
        AccountId accountId,
        TransactionId transactionId,
        Money amount,
        String remark
) {

}
