package co.istad.makara.account.domain.event;

import co.istad.makara.common.domain.valueobject.AccountStatus;
import co.istad.makara.common.domain.valueobject.AccountId;
import co.istad.makara.common.domain.valueobject.CustomerId;
import lombok.Builder;

import java.time.ZonedDateTime;

@Builder
public record AccountFrozenEvent(
        AccountId accountId,
        CustomerId customerId,
        AccountStatus previousStatus,
        AccountStatus newStatus,
        String reason,
        String requestedBy,
        ZonedDateTime createdAt
) {
}
