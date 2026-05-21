package co.istad.makara.gateway_server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResponseCacheFilterConfig implements GlobalFilter, Ordered {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // /pipeline-service/client/account/secured
        String path = exchange.getRequest().getPath().value();

        // Only cache GET requests
        if (!"GET".equals(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }

        // Skip caching for certain paths
        if (path.contains("/actuator") || path.contains("/fallback")) {
            return chain.filter(exchange);
        }

        // Logic here ...

        String cacheKey = path;

        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;

                    return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                        /// Combine all data buffers
                        DataBuffer joinedBuffer = bufferFactory.join(dataBuffers);
                        byte[] content = new byte[joinedBuffer.readableByteCount()];
                        joinedBuffer.read(content);
                        DataBufferUtils.release(joinedBuffer);

                        /// Cache the response if status is OK
                        HttpStatus statusCode = (HttpStatus) getDelegate().getStatusCode();
                        if (statusCode != null && statusCode.is2xxSuccessful()) {
                            String responseBody = new String(content, StandardCharsets.UTF_8);
                            cacheResponse(cacheKey, responseBody).subscribe();
                        }

                        return bufferFactory.wrap(content);
                    }));
                }
                return super.writeWith(body);
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }


    private Mono<Boolean> cacheResponse(String key, String data) {
        return redisTemplate.opsForValue()
                .set(key, data, CACHE_TTL)
                .doOnSuccess(result ->
                        System.out.println("Cached response for key: " + key))
                .doOnError(error ->
                        System.err.println("Failed to cache response: " + error.getMessage()));
    }

    @Override
    public int getOrder() {
        return -2; // Execute before other filters
    }
}
