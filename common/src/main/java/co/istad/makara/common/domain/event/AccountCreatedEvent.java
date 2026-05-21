package co.istad.makara.common.domain.event;

import co.istad.makara.common.domain.valueobject.AccountStatus;
import co.istad.makara.common.domain.valueobject.AccountTypeCode;
import co.istad.makara.common.domain.valueobject.AccountId;
import co.istad.makara.common.domain.valueobject.BranchId;
import co.istad.makara.common.domain.valueobject.CustomerId;
import co.istad.makara.common.domain.valueobject.Money;
import lombok.Builder;

import java.time.ZonedDateTime;

@Builder
public record AccountCreatedEvent(
        AccountId accountId,
        String accountNumber,
        String accountHolder,
        CustomerId customerId,
        AccountTypeCode accountTypeCode,
        BranchId branchId,
        Money initialBalance,
        AccountStatus accountStatus,
        ZonedDateTime createdAt,
        String createdBy
) {

}
