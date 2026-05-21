package co.istad.makara.pipeline.controller;

import co.istad.makara.pipeline.client.CustomerClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/client/customer")
@RequiredArgsConstructor
public class CustomerClientController {

    private final CustomerClient customerClient;

    @GetMapping("/cus")
    @CircuitBreaker(name = "customerService", fallbackMethod = "fallbackCustomers")
    public Map<String, Object> getCustomers() {
        return customerClient.getCustomers();
    }

    public Map<String, Object> fallbackCustomers(Throwable throwable){
        Map<String, Object> response = new HashMap<>();

        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("message", "We're experiencing high traffic right now");
        response.put("customerMessage", "Please try again in a few moments. Your data is safe and we're working to serve you better.");
        response.put("retry", true);
        response.put("timestamp", java.time.Instant.now().toString());

        // Log actual error for debugging (not exposed to user)
        System.err.println("Account service fallback triggered: " + throwable.getMessage());

        return response;
    }
}
