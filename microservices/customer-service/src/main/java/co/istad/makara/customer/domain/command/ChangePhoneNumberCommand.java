package co.istad.makara.customer.domain.command;

import co.istad.makara.common.domain.valueobject.CustomerId;
import lombok.Builder;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

@Builder
public record ChangePhoneNumberCommand(
        @TargetAggregateIdentifier
        CustomerId customerId,
        String phoneNumber
) {
}
