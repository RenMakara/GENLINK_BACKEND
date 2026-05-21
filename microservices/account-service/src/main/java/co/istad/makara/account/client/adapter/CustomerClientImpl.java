package co.istad.makara.account.client.adapter;

import co.istad.makara.account.application.dto.create.CustomerResponse;
import co.istad.makara.account.application.ports.output.external.CustomerClient;
import co.istad.makara.common.domain.valueobject.CustomerId;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;


@Component
public class CustomerClientImpl implements CustomerClient {

    private final WebClient webClient;

    public CustomerClientImpl(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://CUSTOMER/api/customers/").build();
    }

    @Override
    public CustomerResponse getCustomer(CustomerId customerId) {
        return webClient.get()
                .uri("{customerId}", customerId)
                .retrieve()
                .bodyToMono(CustomerResponse.class)
                .block();
    }
}