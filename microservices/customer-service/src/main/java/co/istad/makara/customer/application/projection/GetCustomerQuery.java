package co.istad.makara.customer.application.projection;

import lombok.*;

@Builder
public record GetCustomerQuery(
        int pageNumber,
        int pageSize
) {
}
