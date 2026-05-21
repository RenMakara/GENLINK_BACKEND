// Entity of DDD
package co.istad.makara.customer.domain.aggregate;

import co.istad.makara.common.domain.valueobject.CustomerId;
import co.istad.makara.common.domain.valueobject.CustomerSegmentId;
import co.istad.makara.customer.domain.command.ChangePhoneNumberCommand;
import co.istad.makara.customer.domain.command.CreateCustomerCommand;
import co.istad.makara.customer.domain.event.CustomerCreatedEvent;
import co.istad.makara.customer.domain.event.CustomerPhoneNumberChangedEvent;
import co.istad.makara.customer.domain.valueobject.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.time.LocalDate;
import java.util.List;

@Aggregate(snapshotTriggerDefinition = "customerSnapshotTriggerDefinition")
@NoArgsConstructor
@Getter // this has only getter
@EqualsAndHashCode
@Slf4j
public class CustomerAggregate {

    @AggregateIdentifier // primary key of aggregate
    private CustomerId customerId;

    private CustomerName name;
    private CustomerEmail email;
    private CustomerGender gender;
    private LocalDate dob;
    private Kyc kyc;
    private Address address;
    private Contact contact;
    private String phoneNumber;
    private CustomerSegmentId customerSegmentId;

    List<String> failureMessages;

    // Domain Logic for creating customer
    // Constructor CommandHandler required for first event
    @CommandHandler
    public CustomerAggregate(CreateCustomerCommand createCustomerCommand) {
        // Perform domain logic here
        // Validate email
        // Validate phone

        // Public event -> CustomerCreatedEvent
        CustomerCreatedEvent customerCreatedEvent =
                CustomerCreatedEvent.builder()
                        .customerId(createCustomerCommand.customerId())
                        .name(createCustomerCommand.name())
                        .email(createCustomerCommand.email())
                        .gender(createCustomerCommand.gender())
                        .dob(createCustomerCommand.dob())
                        .kyc(createCustomerCommand.kyc())
                        .address(createCustomerCommand.address())
                        .contact(createCustomerCommand.contact())
                        .phoneNumber(createCustomerCommand.phoneNumber())
                        .customerSegmentId(createCustomerCommand.customerSegmentId())
                        .build();
        AggregateLifecycle.apply(customerCreatedEvent);
    }

    @CommandHandler
    public void handler(ChangePhoneNumberCommand changePhoneNumberCommand) {
        log.info("Handle ChangePhoneNumberCommand: {}", changePhoneNumberCommand);

        CustomerPhoneNumberChangedEvent customerPhoneNumberChangedEvent = CustomerPhoneNumberChangedEvent.builder()
                .customerId(changePhoneNumberCommand.customerId())
                .phoneNumber(changePhoneNumberCommand.phoneNumber())
                .build();
        AggregateLifecycle.apply(customerPhoneNumberChangedEvent);
    }

    @EventSourcingHandler // the last method in aggregate
    public void on(CustomerCreatedEvent customerCreatedEvent) {
        this.customerId = customerCreatedEvent.customerId();
        this.name = customerCreatedEvent.name();
        this.email = customerCreatedEvent.email();
        this.gender = customerCreatedEvent.gender();
        this.dob = customerCreatedEvent.dob();
        this.kyc = customerCreatedEvent.kyc();
        this.address = customerCreatedEvent.address();
        this.contact = customerCreatedEvent.contact();
        this.customerSegmentId = customerCreatedEvent.customerSegmentId();
    }

    @EventSourcingHandler
    public void on(CustomerPhoneNumberChangedEvent customerPhoneNumberChangedEvent) {
        this.customerId = customerPhoneNumberChangedEvent.customerId();
        this.phoneNumber = customerPhoneNumberChangedEvent.phoneNumber();
    }

}


//
