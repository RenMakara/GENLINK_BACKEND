package kh.edu.acleda.campuslink.modules.admin.sanction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminUserSanctionRequest;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminUserSuspendRequest;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminUserWarningRequest;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.response.AdminUserWarningResponse;
import kh.edu.acleda.campuslink.modules.admin.sanction.entity.UserSanction;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionSeverity;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionType;
import kh.edu.acleda.campuslink.modules.admin.sanction.repository.UserSanctionRepository;
import kh.edu.acleda.campuslink.modules.auth.RefreshTokenService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AdminSanctionServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String ADMIN_EMAIL = "moderator@example.com";
    private static final Instant NOW = Instant.parse("2026-09-24T04:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSanctionRepository userSanctionRepository;
    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminUser actor;
    private AdminSanctionService service;

    @BeforeEach
    void setUp() {
        actor = AdminUser.builder().active(true).build();
        service = new AdminSanctionService(
            userRepository,
            userSanctionRepository,
            adminUserRepository,
            refreshTokenService,
            adminAuditService,
            Clock.fixed(NOW, ZoneOffset.UTC));

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(ADMIN_EMAIL, null, java.util.List.of()));
        lenient().when(adminUserRepository.findActiveByUserEmail(ADMIN_EMAIL))
            .thenReturn(Optional.of(actor));
        lenient().when(adminAuditService.toJson(any())).thenReturn("{}");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void banLocksAccountRevokesSessionsAndAuditsTheStateChange() {
        User user = user(AccountStatus.ACTIVE, 4);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(userSanctionRepository.findOpenByUserIdAndType(USER_ID, SanctionType.BAN))
            .thenReturn(Optional.empty());

        service.ban(USER_ID, new AdminUserSanctionRequest("Repeated abusive messages"));

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.BANNED);
        assertThat(user.getTokenVersion()).isEqualTo(5);
        verify(refreshTokenService).revoke(user);
        verify(userRepository).save(user);

        ArgumentCaptor<UserSanction> sanction = ArgumentCaptor.forClass(UserSanction.class);
        verify(userSanctionRepository).save(sanction.capture());
        assertThat(sanction.getValue().getType()).isEqualTo(SanctionType.BAN);
        assertThat(sanction.getValue().getPreviousStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(sanction.getValue().getStartsAt()).isEqualTo(NOW);
        assertThat(sanction.getValue().getExpiresAt()).isNull();

        ArgumentCaptor<AdminAuditEvent> audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(adminAuditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("USER_BAN");
        assertThat(audit.getValue().targetId()).isEqualTo(USER_ID);
        assertThat(audit.getValue().reason()).isEqualTo("Repeated abusive messages");
        assertThat(audit.getValue().beforeJson()).isEqualTo("{}");
        assertThat(audit.getValue().afterJson()).isEqualTo("{}");
    }

    @Test
    void unbanRestoresTheStatusCapturedBeforeTheBan() {
        User user = user(AccountStatus.BANNED, 5);
        UserSanction ban = UserSanction.builder()
            .user(user)
            .adminUser(actor)
            .type(SanctionType.BAN)
            .reason("Repeated abusive messages")
            .previousStatus(AccountStatus.PENDING)
            .startsAt(NOW.minusSeconds(3600))
            .build();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(userSanctionRepository.findOpenByUserIdAndType(USER_ID, SanctionType.BAN))
            .thenReturn(Optional.of(ban));

        service.unban(USER_ID, new AdminUserSanctionRequest("Manual review completed"));

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(user.getTokenVersion()).isEqualTo(6);
        assertThat(ban.getLiftedAt()).isEqualTo(NOW);
        assertThat(ban.getLiftedBy()).isSameAs(actor);
        assertThat(ban.getLiftReason()).isEqualTo("Manual review completed");
        verify(userRepository).save(user);
        verify(refreshTokenService, never()).revoke(user);

        ArgumentCaptor<AdminAuditEvent> audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(adminAuditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("USER_UNBAN");
    }

    @Test
    void suspendUsesFixedDurationAndRevokesSessions() {
        User user = user(AccountStatus.ACTIVE, 0);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        service.suspend(USER_ID, new AdminUserSuspendRequest(
            "Temporary restriction for review", 168));

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(refreshTokenService).revoke(user);

        ArgumentCaptor<UserSanction> sanction = ArgumentCaptor.forClass(UserSanction.class);
        verify(userSanctionRepository).save(sanction.capture());
        assertThat(sanction.getValue().getType()).isEqualTo(SanctionType.SUSPENSION);
        assertThat(sanction.getValue().getPreviousStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(sanction.getValue().getExpiresAt()).isEqualTo(NOW.plusSeconds(168 * 3600L));
    }

    @Test
    void duplicateBanReturnsPublishedErrorAndDoesNotMutateTheUser() {
        User user = user(AccountStatus.BANNED, 2);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(userSanctionRepository.findOpenByUserIdAndType(USER_ID, SanctionType.BAN))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ban(
            USER_ID, new AdminUserSanctionRequest("Repeated abusive messages")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.USER_ALREADY_BANNED);

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.BANNED);
        assertThat(user.getTokenVersion()).isEqualTo(2);
        verify(userSanctionRepository, never()).save(any(UserSanction.class));
        verify(refreshTokenService, never()).revoke(any(User.class));
        verify(adminAuditService).recordFailure(
            eq("USER_BAN"), eq("USER"), eq(USER_ID),
            eq("Repeated abusive messages"), eq(AdminErrorCode.USER_ALREADY_BANNED));
    }

    @Test
    void suspensionRejectsDurationsOutsideThePublishedContract() {
        assertThatThrownBy(() -> service.suspend(USER_ID, new AdminUserSuspendRequest(
            "Temporary restriction for review", 48)))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");
    }

    @Test
    void anonymisedAccountsCannotBeChanged() {
        User user = user(AccountStatus.DELETED, 7);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.suspend(USER_ID, new AdminUserSuspendRequest(
            "Temporary restriction for review", 24)))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.USER_ANONYMISED);

        verify(userSanctionRepository, never()).save(any(UserSanction.class));
        verify(refreshTokenService, never()).revoke(any(User.class));
    }

    @Test
    void warningStoresBilingualMessagesAndClearlyReportsThatDeliveryHasNotHappened() {
        User user = user(AccountStatus.ACTIVE, 3);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        AdminUserWarningRequest request = new AdminUserWarningRequest(
            "Repeated abusive messages",
            SanctionSeverity.MEDIUM,
            "Please keep discussions respectful.",
            "សូមរក្សាការពិភាក្សាឱ្យមានការគោរព។");

        AdminUserWarningResponse response = service.warn(USER_ID, request);

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.severity()).isEqualTo(SanctionSeverity.MEDIUM);
        assertThat(response.delivered()).isFalse();
        assertThat(response.deliveryStatus()).isEqualTo("NOT_DELIVERED");
        assertThat(response.recordedAt()).isEqualTo(NOW);

        ArgumentCaptor<UserSanction> warning = ArgumentCaptor.forClass(UserSanction.class);
        verify(userSanctionRepository).save(warning.capture());
        assertThat(warning.getValue().getType()).isEqualTo(SanctionType.WARNING);
        assertThat(warning.getValue().getSeverity()).isEqualTo(SanctionSeverity.MEDIUM);
        assertThat(warning.getValue().getMessageEn()).isEqualTo(request.messageEn());
        assertThat(warning.getValue().getMessageKm()).isEqualTo(request.messageKm());
        assertThat(warning.getValue().getPreviousStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);

        ArgumentCaptor<AdminAuditEvent> audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(adminAuditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("USER_WARNING");
        assertThat(audit.getValue().targetId()).isEqualTo(USER_ID);
    }

    private User user(AccountStatus status, int tokenVersion) {
        User user = new User();
        user.setAccountStatus(status);
        user.setTokenVersion(tokenVersion);
        user.setEmail("target@example.com");
        user.setFullName("Target User");
        user.setUsername("target");
        return user;
    }
}
