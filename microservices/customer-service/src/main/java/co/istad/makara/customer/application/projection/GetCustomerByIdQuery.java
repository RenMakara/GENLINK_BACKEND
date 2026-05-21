package co.istad.makara.customer.application.projection;

import java.util.UUID;


public record GetCustomerByIdQuery(
        UUID customerId
) {
}
