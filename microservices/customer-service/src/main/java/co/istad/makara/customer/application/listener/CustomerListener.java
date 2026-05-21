package co.istad.makara.customer.application.listener;

import co.istad.makara.customer.application.mapper.CustomerApplicationMapper;
import co.istad.makara.customer.data.entity.CustomerEntity;
import co.istad.makara.customer.data.entity.CustomerSegmentEntity;
import co.istad.makara.customer.data.repository.CustomerRepository;
import co.istad.makara.customer.data.repository.CustomerSegmentRepository;
import co.istad.makara.customer.domain.event.CustomerCreatedEvent;
import co.istad.makara.customer.domain.event.CustomerPhoneNumberChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
@Slf4j
//@Transactional
@ProcessingGroup("customer-group")
public class CustomerListener {

    private final CustomerRepository customerRepository;
    private final CustomerApplicationMapper customerMapper;
    private final CustomerSegmentRepository customerSegmentRepository;

    @EventHandler
    public void on(CustomerCreatedEvent customerCreatedEvent){
        log.info("on CustomerCreatedEvent : {}", customerCreatedEvent);

        CustomerEntity customerEntity =
                customerMapper.customerCreatedEventToCustomerEntity(customerCreatedEvent);

        customerEntity.getAddress().setCustomer(customerEntity);
        customerEntity.getContact().setCustomer(customerEntity);
        customerEntity.getKyc().setCustomer(customerEntity);

        CustomerSegmentEntity customerSegmentEntity = customerSegmentRepository
                .findById(customerCreatedEvent.customerSegmentId().customerSegmentId())
                        .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Segment not found"));
        customerEntity.setCustomerSegment(customerSegmentEntity);

        customerRepository.save(customerEntity);
    }

    @EventHandler
    public void on(CustomerPhoneNumberChangedEvent customerPhoneNumberChangedEvent){
        log.info("on CustomerPhoneNumberChangedEvent: {}", customerPhoneNumberChangedEvent);
        // 1. Find existing customer
        CustomerEntity customerEntity = customerRepository
                .findById(customerPhoneNumberChangedEvent.customerId().value())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Customer not found"
                        )
                );
        // 2. Update phone number
        customerEntity.setPhoneNumber(customerPhoneNumberChangedEvent.phoneNumber());

        // 3. Save (update)
        customerRepository.save(customerEntity);
    }
}
