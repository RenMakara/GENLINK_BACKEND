package co.istad.makara.customer.domain.event;

import co.istad.makara.common.domain.valueobject.CustomerId;
import lombok.Builder;

@Builder
public record CustomerPhoneNumberChangedEvent(
    CustomerId customerId,
    String phoneNumber
) {
}
