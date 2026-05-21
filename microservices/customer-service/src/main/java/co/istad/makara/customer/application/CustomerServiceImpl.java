package co.istad.makara.customer.application;

import co.istad.makara.common.domain.valueobject.CustomerId;
import co.istad.makara.customer.application.dto.create.CreateCustomerRequest;
import co.istad.makara.customer.application.dto.create.CreateCustomerResponse;
import co.istad.makara.customer.application.dto.update.ChangePhoneNumberRequest;
import co.istad.makara.customer.application.dto.update.ChangePhoneNumberResponse;
import co.istad.makara.customer.application.mapper.CustomerApplicationMapper;
import co.istad.makara.customer.domain.command.ChangePhoneNumberCommand;
import co.istad.makara.customer.domain.command.CreateCustomerCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerApplicationMapper customerMapper;
    private final CommandGateway commandGateway;

    @Override
    public ChangePhoneNumberResponse changePhoneNumber(UUID customerId, ChangePhoneNumberRequest changePhoneNumberRequest) {

        // 1. Transfer data from request to command
        ChangePhoneNumberCommand changePhoneNumberCommand = ChangePhoneNumberCommand.builder()
                .customerId(new CustomerId(customerId))
                .phoneNumber(changePhoneNumberRequest.phoneNumber())
                .build();
        log.info("ChangePhoneNumberCommand: {}", changePhoneNumberCommand);

        UUID result = commandGateway.sendAndWait(changePhoneNumberCommand);

        return ChangePhoneNumberResponse.builder()
                .customerId(result)
                .phoneNumber(changePhoneNumberCommand.phoneNumber())
                .message("Phone number changed successfully")
                .build();
    }

    @Override
    public CreateCustomerResponse createCustomer(CreateCustomerRequest createCustomerRequest) {

        // 1. Transfer data from request to command
        CreateCustomerCommand createCustomerCommand = customerMapper
                .createCustomerRequestToCreateCustomerCommand(new CustomerId(UUID.randomUUID()),createCustomerRequest);
        log.info("CreateCustomerCommand: {}", createCustomerCommand);

        // 2. Invoke and handle Axon command gateway
        CustomerId result = commandGateway.sendAndWait(createCustomerCommand);
        log.info("CommandGateway Result: {}", result);

        return CreateCustomerResponse.builder()
                .customerId(createCustomerCommand.customerId().value())
                .message("Customer saved successfully")
                .build();
    }

}