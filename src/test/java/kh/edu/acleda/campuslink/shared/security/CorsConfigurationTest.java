package kh.edu.acleda.campuslink.shared.security;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/** ADM-028 - the admin API gets an explicit origin allow-list; mobile keeps its wildcard. */
class CorsConfigurationTest {

    private static final String ALLOWED = "https://admin.genlink.kh";
    private static final String UNLISTED = "https://evil.example.com";

    private CorsConfigurationSource sourceWith(String... adminOrigins) {
        CorsProperties properties = new CorsProperties();
        properties.setAdminOrigins(List.of(adminOrigins));
        // The filters are irrelevant to this bean; only the properties are read.
        return new SecurityConfig(null, null, null, null, properties).corsConfigurationSource();
    }

    private CorsConfiguration configurationFor(CorsConfigurationSource source, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return source.getCorsConfiguration(request);
    }

    // AC#1 - an unlisted origin is rejected on an admin route.
    @Test
    void anUnlistedOriginIsRejectedOnAnAdminRoute() {
        CorsConfiguration admin = configurationFor(sourceWith(ALLOWED), "/api/v1/admin/users");

        assertThat(admin.checkOrigin(UNLISTED)).isNull();
    }

    @Test
    void aListedOriginIsAllowedOnAnAdminRoute() {
        CorsConfiguration admin = configurationFor(sourceWith(ALLOWED), "/api/v1/admin/users");

        assertThat(admin.checkOrigin(ALLOWED)).isEqualTo(ALLOWED);
    }

    /** The registration order is the whole mechanism. */
    @Test
    void theAdminPolicyWinsOverTheCatchAllPattern() {
        CorsConfiguration admin = configurationFor(sourceWith(ALLOWED), "/api/v1/admin/auth/login");

        assertThat(admin.getAllowedOrigins())
            .as("the admin route must not inherit the mobile wildcard")
            .doesNotContain("*")
            .containsExactly(ALLOWED);
    }

    /** Empty is the MVP default and must fail closed. */
    @Test
    void withNoConfiguredOriginsEveryCrossOriginAdminRequestIsRefused() {
        CorsConfiguration admin = configurationFor(sourceWith(), "/api/v1/admin/users");

        assertThat(admin.checkOrigin(ALLOWED)).isNull();
        assertThat(admin.checkOrigin(UNLISTED)).isNull();
    }

    @Test
    void theAdminPolicyNeverCarriesAWildcard() {
        assertThat(configurationFor(sourceWith(ALLOWED), "/api/v1/admin/users").getAllowedOrigins())
            .doesNotContain("*");
        assertThat(configurationFor(sourceWith(), "/api/v1/admin/users").getAllowedOrigins())
            .doesNotContain("*");
    }

    // AC#2 - mobile routes are unaffected.
    @Test
    void mobileRoutesKeepTheirWildcard() {
        CorsConfigurationSource source = sourceWith(ALLOWED);

        assertThat(configurationFor(source, "/api/v1/profile/me").getAllowedOrigins())
            .containsExactly("*");
        assertThat(configurationFor(source, "/api/v1/auth/login").getAllowedOrigins())
            .containsExactly("*");
    }

    /** An origin refused on the admin API must still be fine for the Flutter app. */
    @Test
    void anOriginRefusedByAdminIsStillAcceptedOnMobile() {
        CorsConfiguration mobile = configurationFor(sourceWith(ALLOWED), "/api/v1/profile/me");

        assertThat(mobile.checkOrigin(UNLISTED)).isNotNull();
    }

    @Test
    void mobileKeepsItsExistingHeaderContract() {
        CorsConfiguration mobile = configurationFor(sourceWith(ALLOWED), "/api/v1/profile/me");

        assertThat(mobile.getAllowedHeaders()).contains("x-auth-token");
        assertThat(mobile.getExposedHeaders()).contains("x-auth-token");
    }

    // Admin policy details
    /** Credentials are needed for the httpOnly refresh cookie (SSOT §10.9). */
    @Test
    void adminAllowsCredentials() {
        CorsConfiguration admin = configurationFor(sourceWith(ALLOWED), "/api/v1/admin/users");

        assertThat(admin.getAllowCredentials()).isTrue();
        assertThat(admin.getAllowedOrigins()).doesNotContain("*");
    }

    /** The correlation id has to survive the round trip for a support report to be useful. */
    @Test
    void adminExposesTheRequestIdHeader() {
        CorsConfiguration admin = configurationFor(sourceWith(ALLOWED), "/api/v1/admin/users");

        assertThat(admin.getAllowedHeaders()).contains("x-request-id");
        assertThat(admin.getExposedHeaders()).contains("x-request-id");
    }

    @Test
    void adminAllowsThePreflightAndMutationMethods() {
        CorsConfiguration admin = configurationFor(sourceWith(ALLOWED), "/api/v1/admin/users");

        assertThat(admin.getAllowedMethods())
            .contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }

    @Test
    void severalOriginsCanBeListed() {
        CorsConfiguration admin = configurationFor(
            sourceWith(ALLOWED, "http://localhost:3000"), "/api/v1/admin/users");

        assertThat(admin.checkOrigin(ALLOWED)).isEqualTo(ALLOWED);
        assertThat(admin.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000");
        assertThat(admin.checkOrigin(UNLISTED)).isNull();
    }

    /** A null from configuration must not become a null field the CORS filter trips over. */
    @Test
    void nullOriginsAreTreatedAsEmpty() {
        CorsProperties properties = new CorsProperties();
        properties.setAdminOrigins(null);

        assertThat(properties.getAdminOrigins()).isEmpty();
    }
}
