package istad.makara.identity.security;



import istad.makara.identity.domain.Role;
import istad.makara.identity.domain.User;
import istad.makara.identity.features.oauth2.JpaRegisteredClientRepository;
import istad.makara.identity.features.role.RoleRepository;
import istad.makara.identity.features.user.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityInit {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JpaRegisteredClientRepository jpaRegisteredClientRepository;

    @PostConstruct
    public void init() {
        if (userRepository.count() == 0) {
            User user = new User();
            user.setUuid(UUID.randomUUID().toString());
            user.setUsername("makara");
            user.setPassword(passwordEncoder.encode("qwer"));
            user.setEmail("it.makara@gmail.com");
            user.setDob(LocalDate.of(2003, 1, 14));
            user.setGender("Male");
            user.setProfileImage("default_profile.jpg");
            user.setCoverImage("default_cover.jpg");
            user.setFamilyName("Ren");
            user.setGivenName("Makara");
            user.setPhoneNumber("0983478329");
            user.setAccountNonExpired(true);
            user.setAccountNonLocked(true);
            user.setCredentialsNonExpired(true);
            user.setIsEnabled(true);

            // Assign role to user
            Set<Role> roles = new HashSet<>();
            roles.add(roleRepository.findByName("SUPER_ADMIN"));
            roles.add(roleRepository.findByName("USER"));
            user.setRoles(roles);

            userRepository.save(user);
            log.info("User has been saved: {}", user.getId());
        }
    }


    @PostConstruct
    void initOAuth2() {

        TokenSettings tokenSettings = TokenSettings.builder()
                .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                .accessTokenTimeToLive(Duration.ofDays(3))
                .reuseRefreshTokens(false) // refresh token rotation
                .refreshTokenTimeToLive(Duration.ofDays(5))
                .build();

        ClientSettings clientSettings = ClientSettings.builder()
                .requireProofKey(true)
                .requireAuthorizationConsent(false)
                .build();

        var itpStandard = RegisteredClient.withId("itp-standard")
                .clientId("itp-standard")
                .clientSecret(passwordEncoder.encode("qwerqwer")) // store in secret manager
                .scopes(scopes -> {
                    scopes.add(OidcScopes.OPENID); // required!
                    scopes.add(OidcScopes.PROFILE);
                    scopes.add(OidcScopes.EMAIL);
                })
                .redirectUris(uris -> {
                    uris.add("http://localhost:9090/login/oauth2/code/itp-standard");
                    uris.add("http://localhost:9090");
                    uris.add("http://localhost:9999/login/oauth2/code/itp-standard");
                    uris.add("http://localhost:9999");
                })
                .postLogoutRedirectUris(uris -> {
                    uris.add("http://localhost:9090");
                })
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC) //TODO: grant_type:client_credentials, client_id & client_secret, redirect_uri
                .authorizationGrantTypes(grantTypes -> {
                    grantTypes.add(AuthorizationGrantType.AUTHORIZATION_CODE);
                    grantTypes.add(AuthorizationGrantType.REFRESH_TOKEN);
                    grantTypes.add(AuthorizationGrantType.CLIENT_CREDENTIALS);
                })
                .clientSettings(clientSettings)
                .tokenSettings(tokenSettings)
                .build();

        var itpFrontBff = RegisteredClient.withId("itp-front-bff")
                .clientId("itp-front-bff")
                .clientSecret(passwordEncoder.encode("qwerqwer")) // store in secret manager
                .scopes(scopes -> {
                    scopes.add(OidcScopes.OPENID); // required!
                    scopes.add(OidcScopes.PROFILE);
                    scopes.add(OidcScopes.EMAIL);
                })
                .redirectUris(uris -> {
                    uris.add("http://localhost:9990/login/oauth2/code/itp-front-bff");
                    uris.add("http://localhost:9990");
                })
                .postLogoutRedirectUris(uris -> {
                    uris.add("http://localhost:9990");
                })
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC) //TODO: grant_type:client_credentials, client_id & client_secret, redirect_uri
                .authorizationGrantTypes(grantTypes -> {
                    grantTypes.add(AuthorizationGrantType.AUTHORIZATION_CODE);
                })
                .clientSettings(clientSettings)
                .tokenSettings(tokenSettings)
                .build();

        var itpAdminBff = RegisteredClient.withId("itp-admin-bff")
                .clientId("itp-admin-bff")
                .clientSecret(passwordEncoder.encode("qwerqwer")) // store in secret manager
                .scopes(scopes -> {
                    scopes.add(OidcScopes.OPENID); // required!
                    scopes.add(OidcScopes.PROFILE);
                    scopes.add(OidcScopes.EMAIL);
                })
                .redirectUris(uris -> {
                    uris.add("http://localhost:9991/login/oauth2/code/itp-admin-bff");
                    uris.add("http://localhost:9991");
                })
                .postLogoutRedirectUris(uris -> {
                    uris.add("http://localhost:9991");
                })
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC) //TODO: grant_type:client_credentials, client_id & client_secret, redirect_uri
                .authorizationGrantTypes(grantTypes -> {
                    grantTypes.add(AuthorizationGrantType.AUTHORIZATION_CODE);
                })
                .clientSettings(clientSettings)
                .tokenSettings(tokenSettings)
                .build();

        RegisteredClient registeredClient = jpaRegisteredClientRepository.findByClientId("itp-standard");
        RegisteredClient registeredClient2 = jpaRegisteredClientRepository.findByClientId("itp-front-bff");
        RegisteredClient registeredClient3 = jpaRegisteredClientRepository.findByClientId("itp-admin-bff");
        log.info("Registered client: {}", registeredClient);
        log.info("Registered client2: {}", registeredClient2);
        log.info("Registered client3: {}", registeredClient3);

        if (registeredClient == null) {
            jpaRegisteredClientRepository.save(itpStandard);
        }
        if (registeredClient2 == null) {
            jpaRegisteredClientRepository.save(itpFrontBff);
        }
        if (registeredClient3 == null) {
            jpaRegisteredClientRepository.save(itpAdminBff);
        }

    }

}