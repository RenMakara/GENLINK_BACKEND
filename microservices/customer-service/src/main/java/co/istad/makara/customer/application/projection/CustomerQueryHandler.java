package co.istad.makara.customer.application.projection;

import co.istad.makara.customer.application.dto.query.CustomerPageResponse;
import co.istad.makara.customer.application.dto.query.CustomerResponse;
import co.istad.makara.customer.application.mapper.CustomerApplicationMapper;
import co.istad.makara.customer.data.entity.CustomerEntity;
import co.istad.makara.customer.data.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.axonframework.queryhandling.QueryHandler;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerQueryHandler {

    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Direction.DESC, "dob");

    private final CustomerRepository customerRepository;
    private final CustomerApplicationMapper mapper;

    @QueryHandler
    public CustomerPageResponse handle(GetCustomerQuery query) {

        int page = Math.max(query.pageNumber(), 0);
        int size = Math.max(query.pageSize(), 1);

        Pageable pageable = PageRequest.of(page, size, DEFAULT_SORT);

        Page<CustomerEntity> customerPage = customerRepository.findAll(pageable);

        List<CustomerResponse> content = customerPage.getContent()
                .stream()
                .map(mapper::toCustomerResponse)
                .toList();

        return CustomerPageResponse.builder()
                .content(content)
                .currentPage(customerPage.getNumber())
                .pageSize(customerPage.getSize())
                .totalElements(customerPage.getTotalElements())
                .totalPages(customerPage.getTotalPages())
                .last(customerPage.isLast())
                .next(customerPage.hasNext())
                .previous(customerPage.hasPrevious())
                .build();
    }

    @QueryHandler
    public CustomerResponse handle(GetCustomerByIdQuery query) {
        UUID id = query.customerId();
        CustomerEntity entity = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
        return mapper.toCustomerResponse(entity);
    }


//    @QueryHandler
//    public CustomPageResponse handle(GetCustomerQuery getCustomerQuery){
//        Pageable pageRequest = PageRequest.of(getCustomerQuery.pageNumber(),  getCustomerQuery.pageSize());
//        Page<CustomerEntity> customerEntities = customerRepository.findAll(pageRequest);
//        return new CustomPageResponse(customerEntities.map(
//                applicationMapper::toCustomerResponse
//        ));
//    }
}