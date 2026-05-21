package co.istad.makara.customer.rest;

import co.istad.makara.customer.application.CustomerService;
import co.istad.makara.customer.application.dto.create.CreateCustomerRequest;
import co.istad.makara.customer.application.dto.create.CreateCustomerResponse;
import co.istad.makara.customer.application.dto.update.ChangePhoneNumberRequest;
import co.istad.makara.customer.application.dto.update.ChangePhoneNumberResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {

    private final CustomerService customerService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CreateCustomerResponse createCustomer(@Valid @RequestBody CreateCustomerRequest createCustomerRequest){
       log.info("Create customer : {}", createCustomerRequest);
       return customerService.createCustomer(createCustomerRequest);
    }

    @PutMapping("/{customerId}/phone-number")
    public ChangePhoneNumberResponse changePhoneNumberResponse(@PathVariable UUID customerId,
                                                               @Valid @RequestBody ChangePhoneNumberRequest changePhoneNumberRequest){
        log.info("Change phone number for customer : {}", changePhoneNumberRequest);
        return customerService.changePhoneNumber(customerId, changePhoneNumberRequest);
    }


}
