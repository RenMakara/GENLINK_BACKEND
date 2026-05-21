package co.istad.makara.customer.application.mapper;


import co.istad.makara.common.domain.valueobject.CustomerId;
import co.istad.makara.customer.application.dto.create.CreateCustomerRequest;
import co.istad.makara.customer.application.dto.query.CustomerResponse;
import co.istad.makara.customer.data.entity.CustomerEntity;
import co.istad.makara.customer.domain.command.CreateCustomerCommand;
import co.istad.makara.customer.domain.event.CustomerCreatedEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerApplicationMapper {

    CustomerResponse toCustomerResponse(CustomerEntity customerEntity);

    CreateCustomerCommand createCustomerRequestToCreateCustomerCommand
            (CustomerId customerId, CreateCustomerRequest createCustomerRequest);

    @Mapping(source = "customerId.value", target = "customerId")
    @Mapping(target = "customerSegment", ignore = true)
    @Mapping(target = "failureMessages", ignore = true)
    @Mapping(target = "kyc.customer", ignore = true)
    @Mapping(target = "address.customer", ignore = true)
    @Mapping(target = "contact.customer", ignore = true)
    CustomerEntity customerCreatedEventToCustomerEntity(CustomerCreatedEvent customerCreatedEvent);
}
