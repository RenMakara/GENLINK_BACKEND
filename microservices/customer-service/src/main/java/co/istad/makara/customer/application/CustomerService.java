package co.istad.makara.customer.application;

import co.istad.makara.customer.application.dto.create.CreateCustomerRequest;
import co.istad.makara.customer.application.dto.create.CreateCustomerResponse;
import co.istad.makara.customer.application.dto.update.ChangePhoneNumberRequest;
import co.istad.makara.customer.application.dto.update.ChangePhoneNumberResponse;

import java.util.Optional;
import java.util.UUID;

public interface CustomerService {
    ChangePhoneNumberResponse changePhoneNumber(UUID customerId, ChangePhoneNumberRequest changePhoneNumberRequest);
    CreateCustomerResponse createCustomer(CreateCustomerRequest createCustomerRequest);
}
