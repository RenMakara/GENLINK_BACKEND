package co.istad.makara.customer.application.dto.create;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CreateCustomerResponse(
        @NotNull
        UUID customerId,
        @NotBlank
        String message
) {
}
