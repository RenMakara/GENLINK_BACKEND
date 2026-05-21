package co.istad.makara.customer.domain.event;


import co.istad.makara.common.domain.valueobject.CustomerId;
import co.istad.makara.common.domain.valueobject.CustomerSegmentId;
import co.istad.makara.customer.domain.valueobject.*;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record CustomerCreatedEvent(
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
