package co.istad.makara.customer.application.dto.create;

import co.istad.makara.common.domain.valueobject.CustomerSegmentId;
import co.istad.makara.customer.domain.valueobject.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateCustomerRequest(
        @NotNull
        CustomerName name,
        @NotNull
        CustomerEmail email,
        @NotNull
        CustomerGender gender,
        @NotNull
        LocalDate dob,
        @NotNull
        Kyc kyc,
        @NotNull
        Address address,
        @NotNull
        Contact contact,
        @NotBlank @NotNull
        String phoneNumber,
        @NotNull
        CustomerSegmentId customerSegmentId
) {
}
