package kh.edu.acleda.campuslink.shared.security;

import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditWriter;
import kh.edu.acleda.campuslink.modules.admin.audit.entity.AdminAuditLog;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import kh.edu.acleda.campuslink.modules.admin.rbac.AdminIdentity;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.AdminPermissionCache;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.admin.sanction.AdminSanctionService;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminUserSanctionRequest;
import kh.edu.acleda.campuslink.modules.admin.sanction.entity.UserSanction;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionType;
import kh.edu.acleda.campuslink.modules.admin.sanction.repository.UserSanctionRepository;
import kh.edu.acleda.campuslink.modules.auth.RefreshTokenService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.Role;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.GlobalExceptionHandler;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pins five critical security behaviors. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityRegressionTest {

    @Nested
    @DisplayName("S1 - a typ=MOBILE token is rejected on /api/v1/admin/**")
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class S1MobileTokenOnAdminRoute {

        /** Prevents mobile tokens from reaching admin routes. */
        @Mock private JwtService jwtService;
        @Mock private UserDetailsService userDetailsService;
        @Mock private FilterChain filterChain;

        @AfterEach
        void clear() {
            SecurityContextHolder.clearContext();
        }

        @Test
        void aMobileTokenIsRefusedOnTheAdminApi() throws Exception {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
            when(jwtService.validateToken("t")).thenReturn(true);
            when(jwtService.getTokenType("t")).thenReturn(TokenType.MOBILE);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/users");
            request.setRequestURI("/api/v1/admin/users");
            request.addHeader("Authorization", "Bearer t");

            filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE))
                .isEqualTo(JwtAuthenticationFilter.TOKEN_TYPE_INVALID);
            // Short-circuits before the user is even loaded.
            verify(userDetailsService, never()).loadUserByUsername(any());
        }

        /** Symmetric: the two sessions are not interchangeable in either direction. */
        @Test
        void anAdminTokenIsRefusedOnTheMobileApi() throws Exception {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
            when(jwtService.validateToken("t")).thenReturn(true);
            when(jwtService.getTokenType("t")).thenReturn(TokenType.ADMIN);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/profile/me");
            request.setRequestURI("/api/v1/profile/me");
            request.addHeader("Authorization", "Bearer t");

            filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    @DisplayName("S2 - a SUSPENDED user is denied on the next request")
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class S2SuspendedUserIsDenied {

        @Mock private UserRepository userRepository;
        @Mock private AdminAuthorityProvider adminAuthorityProvider;

        /** Protects against: suspension becoming cosmetic. */
        @ParameterizedTest(name = "{0} is disabled")
        @EnumSource(value = AccountStatus.class, names = {"PENDING", "BANNED", "SUSPENDED", "DELETED"})
        void everyNonActiveStatusIsDisabled(AccountStatus status) {
            CustomUserDetailsService service =
                new CustomUserDetailsService(userRepository, adminAuthorityProvider);
            User user = new User();
            user.setEmail("someone@genlink.kh");
            user.setPasswordHash("hash");
            user.setAccountStatus(status);
            user.setRole(Role.USER);
            when(userRepository.findByEmail("someone@genlink.kh")).thenReturn(java.util.Optional.of(user));
            when(adminAuthorityProvider.authoritiesFor(any())).thenReturn(java.util.List.of());

            UserDetails details = service.loadUserByUsername("someone@genlink.kh");

            assertThat(details.isEnabled())
                .as("%s must not be able to authenticate", status)
                .isFalse();
        }

        @Test
        void anActiveUserIsStillEnabled() {
            CustomUserDetailsService service =
                new CustomUserDetailsService(userRepository, adminAuthorityProvider);
            User user = new User();
            user.setEmail("someone@genlink.kh");
            user.setPasswordHash("hash");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setRole(Role.USER);
            when(userRepository.findByEmail("someone@genlink.kh")).thenReturn(java.util.Optional.of(user));
            when(adminAuthorityProvider.authoritiesFor(any())).thenReturn(java.util.List.of());

            assertThat(service.loadUserByUsername("someone@genlink.kh").isEnabled()).isTrue();
        }

        /** A soft-deleted ACTIVE row must also be refused - status alone is not enough. */
        @Test
        void aSoftDeletedUserIsDisabledEvenWhileActive() {
            CustomUserDetailsService service =
                new CustomUserDetailsService(userRepository, adminAuthorityProvider);
            User user = new User();
            user.setEmail("someone@genlink.kh");
            user.setPasswordHash("hash");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setDeletedAt(Instant.now());
            user.setRole(Role.USER);
            when(userRepository.findByEmail("someone@genlink.kh")).thenReturn(java.util.Optional.of(user));
            when(adminAuthorityProvider.authoritiesFor(any())).thenReturn(java.util.List.of());

            assertThat(service.loadUserByUsername("someone@genlink.kh").isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("S3 - a missing permission returns 403, not 404")
    class S3MissingPermissionIsForbidden {

        /** Protects against: the mobile 404 convention being "helpfully" applied to admin routes (SSOT §8 C-1). */
        @Test
        void anAccessDeniedExceptionBecomesA403WithTheForbiddenCode() {
            ResponseEntity<ApiResponse<Void>> response =
                new GlobalExceptionHandler().handleAccessDenied(
                    new AccessDeniedException("Access is denied"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getErrorCode()).isEqualTo("FORBIDDEN");
            assertThat(response.getBody().isSuccess()).isFalse();
        }

        @Test
        void itIsNeverReportedAsNotFound() {
            ResponseEntity<ApiResponse<Void>> response =
                new GlobalExceptionHandler().handleAccessDenied(
                    new AccessDeniedException("Access is denied"));

            assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().getErrorCode()).isNotEqualTo("NOT_FOUND");
        }

        /** The message must not disclose what was being reached, only that it was refused. */
        @Test
        void theBodyDoesNotEchoTheAttemptedResource() {
            ResponseEntity<ApiResponse<Void>> response =
                new GlobalExceptionHandler().handleAccessDenied(
                    new AccessDeniedException("Access denied to /api/v1/admin/finance/payouts"));

            assertThat(response.getBody().getMessage()).doesNotContain("finance");
        }
    }

    @Nested
    @DisplayName("S4 - an audit row is written when an admin action FAILS")
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class S4FailuresAreAudited {

        @Mock private AdminAuditWriter writer;
        @Mock private AdminUserRepository adminUserRepository;
        @Mock private AdminPermissionCache permissionCache;

        private AdminAuditService service() {
            return new AdminAuditService(writer, adminUserRepository, permissionCache,
                new com.fasterxml.jackson.databind.ObjectMapper());
        }

        /** Protects against: the {@code SecurityAuditService} rollback bug repeating. */
        @Test
        void aFailedActionIsWrittenInItsOwnTransaction() {
            when(permissionCache.identityFor(any())).thenReturn(AdminIdentity.NONE);

            service().recordFailure("USER_BAN", "USER", UUID.randomUUID(),
                "spamming", "USER_ALREADY_BANNED");

            ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
            // The whole point: a NEW transaction, so the caller's rollback cannot erase it.
            verify(writer).writeInNewTransaction(captor.capture());
            verify(writer, never()).writeInCurrentTransaction(any());
            assertThat(captor.getValue().getStatus()).isEqualTo(AuditStatus.FAILURE);
            assertThat(captor.getValue().getErrorCode()).isEqualTo("USER_ALREADY_BANNED");
        }

        /** The mirror image, and equally load-bearing: a SUCCESS row must join the caller's transaction. */
        @Test
        void aSuccessfulActionJoinsTheCallersTransaction() {
            when(permissionCache.identityFor(any())).thenReturn(AdminIdentity.NONE);

            service().recordSuccess("USER_BAN", "USER", UUID.randomUUID(), "spamming");

            verify(writer).writeInCurrentTransaction(any());
            verify(writer, never()).writeInNewTransaction(any());
        }
    }

    @Nested
    @DisplayName("S5 - lifting a sanction on a PENDING user returns them to PENDING")
    class S5UnbanRestoresPreviousStatus {

        @AfterEach
        void clearContext() {
            SecurityContextHolder.clearContext();
        }

        /** The bug it guards: unban setting {@code ACTIVE} unconditionally. */
        @Test
        void liftingASanctionRestoresTheStatusTheUserHadBefore() {
            UUID userId = UUID.randomUUID();
            Instant now = Instant.parse("2026-09-24T04:00:00Z");
            User user = new User();
            user.setAccountStatus(AccountStatus.BANNED);
            user.setTokenVersion(1);
            user.setEmail("target@example.com");
            user.setFullName("Target User");
            user.setUsername("target");

            AdminUser actor = AdminUser.builder().active(true).build();
            UserSanction sanction = UserSanction.builder()
                .user(user)
                .adminUser(actor)
                .type(SanctionType.BAN)
                .reason("Repeated abusive messages")
                .previousStatus(AccountStatus.PENDING)
                .startsAt(now.minusSeconds(3600))
                .build();

            UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
            UserSanctionRepository sanctionRepository =
                org.mockito.Mockito.mock(UserSanctionRepository.class);
            AdminUserRepository adminUserRepository =
                org.mockito.Mockito.mock(AdminUserRepository.class);
            RefreshTokenService refreshTokenService =
                org.mockito.Mockito.mock(RefreshTokenService.class);
            AdminAuditService auditService = org.mockito.Mockito.mock(AdminAuditService.class);

            when(userRepository.findByIdForUpdate(userId)).thenReturn(java.util.Optional.of(user));
            when(sanctionRepository.findOpenByUserIdAndType(userId, SanctionType.BAN))
                .thenReturn(java.util.Optional.of(sanction));
            when(adminUserRepository.findActiveByUserEmail("moderator@example.com"))
                .thenReturn(java.util.Optional.of(actor));
            when(auditService.toJson(any())).thenReturn("{}");

            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("moderator@example.com", null,
                    java.util.List.of()));

            AdminSanctionService service = new AdminSanctionService(
                userRepository,
                sanctionRepository,
                adminUserRepository,
                refreshTokenService,
                auditService);

            service.unban(userId, new AdminUserSanctionRequest("Manual review completed"));

            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
        }

        /** Active in the meantime: the mechanism that makes the behaviour above possible. */
        @Test
        void aSanctionCannotBeRecordedWithoutThePreviousStatus() throws Exception {
            var column = UserSanction.class.getDeclaredField("previousStatus")
                .getAnnotation(jakarta.persistence.Column.class);

            assertThat(column).isNotNull();
            assertThat(column.nullable())
                .as("previous_status must be NOT NULL, or unban has nothing to restore")
                .isFalse();
            assertThat(UserSanction.class.getDeclaredField("previousStatus").getType())
                .isEqualTo(AccountStatus.class);
        }

        /** PENDING and ACTIVE must remain distinct values. */
        @Test
        void pendingAndActiveAreDistinctStatuses() {
            assertThat(AccountStatus.PENDING).isNotEqualTo(AccountStatus.ACTIVE);
            assertThat(AccountStatus.valueOf("PENDING")).isNotNull();
        }
    }

    /** Unused import guard - keeps the S1 helper honest if it is ever refactored away. */
    @Test
    void theSuiteCoversAllFiveRegressions() {
        assertThat(SecurityRegressionTest.class.getDeclaredClasses())
            .as("all five S-numbers must be present; deleting one should break this")
            .hasSize(5);
    }

    @SuppressWarnings("unused")
    private static UserDetails unusedHelper() {
        return new AuthenticatedUser("x@y.z", "h", true, 1,
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
