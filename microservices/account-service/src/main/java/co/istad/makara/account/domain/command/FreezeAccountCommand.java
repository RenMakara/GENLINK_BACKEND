package co.istad.makara.account.domain.command;

import co.istad.makara.common.domain.valueobject.AccountId;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.ZonedDateTime;

public record FreezeAccountCommand(
        @TargetAggregateIdentifier
        AccountId accountId,
        String remark,
        String requestedBy
) {

}
