package co.istad.makara.pipeline.controller;


import co.istad.makara.pipeline.client.AccountClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
//import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/client/account")
@Slf4j
public class AccountClientController {

    private final AccountClient accountClient;
    private final CircuitBreaker circuitBreaker;

    public AccountClientController(AccountClient accountClient,
                                   CircuitBreakerRegistry registry) {
        this.accountClient = accountClient;
        circuitBreaker = registry.circuitBreaker("account");
    }

    //@CircuitBreaker(name = "account", fallbackMethod = "getSecuredDataFallback")
    @GetMapping("/secured")
    public Map<String, Object> getSecuredData() {

        log.debug("getSecuredData");
        //return accountClient.getSecuredData();
        try {
            return circuitBreaker.executeSupplier(accountClient::getAccounts);
        } catch (CallNotPermittedException e) {
            return Map.of("data", e.getMessage());
        } catch (Exception e) {
            System.out.println("Error" + e.getMessage());
            return Map.of("data", e.getMessage());
        }
    }

//    public Map<String, Object> getSecuredDataFallback(Throwable t) {
//        return Map.of("data", "default value");
//    }



}
