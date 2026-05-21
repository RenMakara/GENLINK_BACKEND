package co.istad.makara.customer.domain.command;

import co.istad.makara.common.domain.valueobject.CustomerId;
import co.istad.makara.common.domain.valueobject.CustomerSegmentId;
import co.istad.makara.customer.domain.valueobject.*;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.LocalDate;

public record CreateCustomerCommand(
        @TargetAggregateIdentifier
        CustomerId customerId,
        CustomerName name,
        CustomerEmail email,
        CustomerGender gender,
        LocalDate dob,
        Kyc kyc,
        Address address,
        Contact contact,
        String phoneNumber,
        CustomerSegmentId customerSegmentId
) {
}
