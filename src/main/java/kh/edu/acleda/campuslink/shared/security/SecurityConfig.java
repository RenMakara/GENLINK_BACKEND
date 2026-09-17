package kh.edu.acleda.campuslink.shared.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

import kh.edu.acleda.campuslink.modules.admin.auth.AdminEnrolmentGuardFilter;
import kh.edu.acleda.campuslink.modules.admin.auth.AdminSessionRevocationFilter;
import kh.edu.acleda.campuslink.modules.admin.bootstrap.AdminBootstrapProperties;
import kh.edu.acleda.campuslink.shared.crypto.CryptoProperties;
import kh.edu.acleda.campuslink.shared.localization.LocalizationProperties;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, LocalizationProperties.class, CryptoProperties.class,
    CorsProperties.class, AdminBootstrapProperties.class})
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final AdminEnrolmentGuardFilter adminEnrolmentGuardFilter;
    private final AdminSessionRevocationFilter adminSessionRevocationFilter;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                          AdminEnrolmentGuardFilter adminEnrolmentGuardFilter,
                          AdminSessionRevocationFilter adminSessionRevocationFilter,
                          CorsProperties corsProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.adminEnrolmentGuardFilter = adminEnrolmentGuardFilter;
        this.adminSessionRevocationFilter = adminSessionRevocationFilter;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/google",
                    "/api/v1/auth/verify-email",
                    "/api/v1/auth/verify-otp",
                    "/api/v1/auth/resend-otp",
                    // A PENDING user has no token yet, so this must be reachable
                    // unauthenticated; it is written not to disclose account existence.
                    "/api/v1/auth/resend-verification-otp",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/refresh-token",
                    "/api/v1/auth/forgot-password",
                    "/api/v1/auth/verify-reset-otp",
                    "/api/v1/auth/reset-password",
                    // An admin arriving at the portal has no token yet. Only the password
                    // step is open: it issues an MFA challenge that authenticates nothing,
                    // so opening it grants no access on its own. Every other
                    // /api/v1/admin/** route stays behind the rule below.
                    "/api/v1/admin/auth/login",
                    // Enrolment is authorised by the 5-minute challenge token from /login,
                    // which the JWT filter refuses by design - so these cannot be
                    // "authenticated" routes. AdminMfaEnrolmentService validates the token
                    // itself and is the only guard in front of them. An admin token is only
                    // issued after TOTP, so requiring one here would mean needing TOTP to set
                    // up TOTP, and the bootstrap admin could never sign in.
                    "/api/v1/admin/auth/mfa/enrol",
                    "/api/v1/admin/auth/mfa/confirm",
                    // Public because the caller's access token has expired - that is what
                    // they are here to replace. The refresh token in the body is the
                    // credential, and AdminSessionService re-checks that the account is
                    // still an active, enrolled admin before minting anything.
                    "/api/v1/admin/auth/refresh",
                    // Also challenge-authorised: the caller has proved a password but holds
                    // no session yet, which is precisely what this endpoint issues.
                    "/api/v1/admin/auth/mfa/verify",
                    "/api/v1/search",
                    "/api/v1/search/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/actuator/health",
                    "/ws/**")
                .permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // Before the enrolment guard: a signed-out token should be refused outright rather
            // than evaluated for anything else. Both run after authentication, so they can see
            // who the caller claims to be.
            .addFilterAfter(adminSessionRevocationFilter, JwtAuthenticationFilter.class)
            // An admin whose MFA was reset while holding a live token must not keep reaching
            // the admin surface.
            .addFilterAfter(adminEnrolmentGuardFilter, AdminSessionRevocationFilter.class);
        return http.build();
    }


    /**
     * Two policies, admin first.
     *
     * <p>{@code UrlBasedCorsConfigurationSource} returns the <strong>first</strong> registered
     * pattern that matches, not the most specific one, so the order of these two registrations
     * is what makes the admin rule apply at all. Registering {@code /**} first would silently
     * hand the admin API the mobile wildcard, and nothing about the code would look wrong.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/admin/**", adminCorsConfiguration());
        source.registerCorsConfiguration("/**", mobileCorsConfiguration());
        return source;
    }

    /**
     * The admin portal: an explicit allow-list, never a wildcard (SSOT §10.9).
     *
     * <p>Empty by default. Under the MVP's path-based routing the portal and API share one
     * origin, so nothing is cross-origin and CORS never runs; the list exists for the eventual
     * subdomain split. Empty means every cross-origin admin request is refused, which is the
     * safe reading of "not configured yet".
     */
    private CorsConfiguration adminCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAdminOrigins());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("authorization", "content-type", "x-request-id"));
        // So the portal can quote a request id in a support report - it is the same id that
        // appears in our logs and in admin_audit_logs (ADM-008).
        configuration.setExposedHeaders(List.of("x-request-id"));
        // Legal only because the origins above are explicit; the browser refuses this
        // combination with a wildcard. Needed for the httpOnly refresh cookie (SSOT §10.9).
        configuration.setAllowCredentials(true);
        return configuration;
    }

    /**
     * Mobile, unchanged.
     *
     * <p>Still a wildcard, and deliberately left alone: the Flutter app is not a browser, sends
     * a bearer token rather than a cookie, and is not subject to the same-origin policy that
     * makes a wildcard dangerous. Narrowing it here would be an unrelated change to a shipped
     * client (SSOT §10.9, ADM-028 AC#2).
     */
    private CorsConfiguration mobileCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("authorization", "content-type", "x-auth-token"));
        configuration.setExposedHeaders(List.of("x-auth-token"));
        return configuration;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
