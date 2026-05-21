package co.istad.makara.gateway_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
@RequiredArgsConstructor
public class FallbackController {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Fallback for account service - tries to get data from Redis cache
     */
    @GetMapping(value = "/pipeline-service/client/account/secured")
    public Mono<Object> accountServiceFallback(ServerWebExchange exchange) {

        String path = exchange.getRequest().getPath().value();
        // /fallback/pipeline

        String cacheKey = path.replace("/fallback", "");
        // /pipeline

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .flatMap(json -> {
                    try {
                        Object data = objectMapper.readValue(json, Object.class);
                        return Mono.just(Map.of("cache", data));
                    } catch (Exception ex) {
                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("error", "Invalid JSON data in cache")));
                    }
                })
                .switchIfEmpty(Mono.just(
                        ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", "Cache not found"))
                ));
    }
}

