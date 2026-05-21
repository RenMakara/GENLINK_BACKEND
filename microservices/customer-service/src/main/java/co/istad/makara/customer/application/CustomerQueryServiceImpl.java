package co.istad.makara.customer.application;
import co.istad.makara.customer.application.dto.query.CustomerPageResponse;
import co.istad.makara.customer.application.dto.query.CustomerResponse;
import co.istad.makara.customer.application.projection.GetCustomerByIdQuery;
import co.istad.makara.customer.application.projection.GetCustomerQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerQueryServiceImpl implements CustomerQueryService{

    private final EventStore eventStore;
    private final QueryGateway queryGateway;

    @Override
    public CustomerPageResponse getAllCustomers(int pageNumber, int pageSize) {
        return queryGateway.query(
                new GetCustomerQuery(pageNumber, pageSize),
                ResponseTypes.instanceOf(CustomerPageResponse.class)
        ).join();
    }

    @Override
    public List<?> getCustomerHistory(UUID customerId) {
        return eventStore.readEvents(customerId.toString())
                .asStream()
                .map(Message::getPayload)
                .toList();

    }

    @Override
    public CustomerResponse getCustomerById(UUID customerId) {
        GetCustomerByIdQuery getCustomerByIdQuery = new GetCustomerByIdQuery(customerId);
        return queryGateway.query(getCustomerByIdQuery, ResponseTypes.instanceOf(CustomerResponse.class)).join();

    }
//    @Override
//    public CustomPageResponse getAllCustomers(int pageNumber, int pageSize){
//        GetCustomerQuery getCustomerQuery = GetCustomerQuery.builder()
//                .pageNumber(pageNumber)
//                .pageSize(pageSize)
//                .build();
//        return queryGateway
//                .query(getCustomerQuery, ResponseTypes.instanceOf(CustomPageResponse.class))
//                .join();
//    }


}
