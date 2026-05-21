package co.istad.makara.customer.rest;

import co.istad.makara.customer.application.CustomerQueryService;
import co.istad.makara.customer.application.dto.query.CustomPageResponse;
import co.istad.makara.customer.application.dto.query.CustomerPageResponse;
import co.istad.makara.customer.application.dto.query.CustomerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerQueryController {


    private final CustomerQueryService customerQueryService;


    @GetMapping("/{customerId}/history")
    public List<?> getCustomerHistory(@PathVariable("customerId") UUID customerId){
        return customerQueryService.getCustomerHistory(customerId);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerPageResponse getAllCustomers(
            @RequestParam(defaultValue = "0", required = false) int pageNumber,
            @RequestParam(defaultValue = "10", required = false) int pageSize
    ){
        return customerQueryService.getAllCustomers(pageNumber, pageSize);
    }

    @GetMapping("/{customerId}")
    public CustomerResponse getCustomerById(@PathVariable("customerId") UUID customerId){
        return customerQueryService.getCustomerById(customerId);
    }
}

/** Flow
 * Controller → CustomerQueryService → QueryGateway
                                           ↓
               CustomerProjection (@QueryHandler)
                                           ↓
               CustomerRepository (paginated)
                                           ↓
               CustomerApplicationMapper
                                           ↓
               PageResponse<CustomerEntityResponse>
*/