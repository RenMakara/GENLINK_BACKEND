package co.istad.makara.customer.application;

import co.istad.makara.customer.application.dto.query.CustomPageResponse;
import co.istad.makara.customer.application.dto.query.CustomerPageResponse;
import co.istad.makara.customer.application.dto.query.CustomerResponse;

import java.util.List;
import java.util.UUID;


public interface CustomerQueryService {

    CustomerPageResponse getAllCustomers(int pageNumber, int pageSize);

    List<?> getCustomerHistory(UUID customerId);

    CustomerResponse getCustomerById(UUID customerId);

}
