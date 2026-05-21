package co.istad.makara.account.domain.command;

import co.istad.makara.common.domain.valueobject.AccountStatus;
import co.istad.makara.common.domain.valueobject.AccountTypeCode;
import co.istad.makara.common.domain.valueobject.AccountId;
import co.istad.makara.common.domain.valueobject.BranchId;
import co.istad.makara.common.domain.valueobject.CustomerId;
import co.istad.makara.common.domain.valueobject.Money;
import org.axonframework.modelling.command.TargetAggregateIdentifier;


public record CreateAccountCommand(
        @TargetAggregateIdentifier
        AccountId accountId,
        String accountNumber,
        String accountHolder,
        CustomerId customerId,
        AccountTypeCode accountTypeCode,
        BranchId branchId,
        Money initialBalance,
        AccountStatus accountStatus
) {

}
