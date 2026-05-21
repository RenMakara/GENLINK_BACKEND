package co.istad.makara.account.application.ports.output.external;

import co.istad.makara.account.application.dto.create.CustomerResponse;
import co.istad.makara.common.domain.valueobject.CustomerId;

public interface CustomerClient {
    CustomerResponse getCustomer(CustomerId customerId);
}
