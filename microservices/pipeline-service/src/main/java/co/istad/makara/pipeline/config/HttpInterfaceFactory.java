package co.istad.makara.pipeline.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Component
@RequiredArgsConstructor
public class HttpInterfaceFactory {

    private final OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager;
    private final WebClient.Builder loadBalancedWebClientBuilder;


    public <T> T createNormalClient(String baseUrl, Class<T> interfaceClass) {
        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        return createClient(webClient, interfaceClass);
    }

    // 2 method invoke from 1 method
    public <T> T createLoadbalacdClient(String baseUrl, Class<T> interfaceClass) {

        var oauth2 = new ServletOAuth2AuthorizedClientExchangeFilterFunction(oAuth2AuthorizedClientManager);
        oauth2.setDefaultClientRegistrationId("itp-standard");

        /// This config use for when use load balance
        WebClient webClient = loadBalancedWebClientBuilder
                .baseUrl(baseUrl)
                .apply(oauth2.oauth2Configuration())
                .build();
        return createClient(webClient, interfaceClass);

        /// This config ues for when not use load balance
//        WebClient webClient = WebClient.builder()
//                .baseUrl(baseUrl)
//                .apply(oauth2.oauth2Configuration())
//                .build();
//        return createClient(webClient, interfaceClass);
    }

    // 1 method
    public <T> T createClient(WebClient webClient, Class<T> interfaceClass) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(webClient))
                .build();
        return factory.createClient(interfaceClass);
    }
}
