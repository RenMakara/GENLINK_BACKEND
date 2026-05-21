package co.istad.makara.pipeline.client;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.Map;

@HttpExchange("/api/v1/accounts")
public interface AccountClient {

    @GetExchange
    Map<String, Object> getAccounts();
}
