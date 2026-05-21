package co.istad.makara.pipeline.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
@RequiredArgsConstructor
public class OAuth2AuthorizedClientManagerConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    @Bean
    public OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager() {

        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();

        AuthorizedClientServiceOAuth2AuthorizedClientManager manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository,
                oAuth2AuthorizedClientService
        );

        manager.setAuthorizedClientProvider(provider);

        return manager;
    }

}