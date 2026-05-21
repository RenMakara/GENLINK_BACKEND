package co.istad.makara.customer.domain.valueobject;

import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public record Address(
        UUID addressId,
        String line,
        String city,
        String country,
        String zipCode
) {
}
