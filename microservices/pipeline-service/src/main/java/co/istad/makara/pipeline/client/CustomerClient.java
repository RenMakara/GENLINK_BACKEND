package co.istad.makara.pipeline.client;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.Map;

@HttpExchange
public interface CustomerClient {

    @GetExchange("/api/v1/customers")
    Map<String, Object> getCustomers();

}
