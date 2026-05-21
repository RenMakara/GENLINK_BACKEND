package istad.makara.identity.security;

import istad.makara.identity.features.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;


@Configuration
@RequiredArgsConstructor
public class CustomTokenCustomizer {

    private final UserRepository userRepository;

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> customize() {
        return context -> {
            if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
                String username = context.getPrincipal().getName();

                var user = userRepository.findByUsername(username)
                        .orElseThrow(() -> new RuntimeException("User not found: " + username));

                context.getClaims()
                        .claim("uuid", user.getUuid())
                        .claim("email", user.getEmail())
                        .claim("family_name", user.getFamilyName())
                        .claim("given_name", user.getGivenName())
                        .claim("phone_number", user.getPhoneNumber())
                        .claim("profile_image", user.getProfileImage())
                        .claim("gender", user.getGender());
            }
        };
    }
}