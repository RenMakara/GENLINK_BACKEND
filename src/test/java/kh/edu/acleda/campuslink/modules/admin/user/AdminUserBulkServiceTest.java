package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.audit.entity.AdminAuditLog;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import kh.edu.acleda.campuslink.modules.admin.audit.repository.AdminAuditLogRepository;
import kh.edu.acleda.campuslink.modules.admin.sanction.AdminSanctionService;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminUserSanctionRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminBulkBadgeRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminBulkBanRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminBulkNotifyRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminBulkActionResponse;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Badge;
import kh.edu.acleda.campuslink.modules.entertainment.repository.BadgeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.UserBadgeRepository;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationTemplate;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationTemplateRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTemplateRenderer;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminUserBulkServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
    private static final UUID BATCH_ID = UUID.randomUUID();

    @Mock private AdminSanctionService adminSanctionService;
    @Mock private UserRepository userRepository;
    @Mock private NotificationTemplateRepository notificationTemplateRepository;
    @Mock private NotificationEmitter notificationEmitter;
    @Mock private BadgeRepository badgeRepository;
    @Mock private UserBadgeRepository userBadgeRepository;
    @Mock private AdminAuditLogRepository adminAuditLogRepository;
    @Mock private AdminAuditService adminAuditService;

    private AdminUserBulkService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserBulkService(
            adminSanctionService,
            userRepository,
            notificationTemplateRepository,
            new NotificationTemplateRenderer(),
            notificationEmitter,
            badgeRepository,
            userBadgeRepository,
            adminAuditLogRepository,
            adminAuditService,
            Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(adminAuditLogRepository.findByBatchId(any(UUID.class))).thenReturn(List.of());
    }

    @Test
    void bulkBanContinuesAfterOneUserFails() {
        UUID first = UUID.randomUUID();
        UUID invalid = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        doNothing()
            .when(adminSanctionService)
            .ban(eq(first), any(AdminUserSanctionRequest.class), eq(BATCH_ID));
        doThrow(new NotFoundException("User not found"))
            .when(adminSanctionService)
            .ban(eq(invalid), any(AdminUserSanctionRequest.class), eq(BATCH_ID));
        doNothing()
            .when(adminSanctionService)
            .ban(eq(third), any(AdminUserSanctionRequest.class), eq(BATCH_ID));

        var response = service.ban(new AdminBulkBanRequest(
            BATCH_ID,
            List.of(first, invalid, third),
            "Repeated abuse confirmed"));

        assertThat(response.requested()).isEqualTo(3);
        assertThat(response.succeeded()).withFailMessage("response=%s", response).isEqualTo(2);
        assertThat(response.failed()).singleElement().satisfies(failure -> {
            assertThat(failure.id()).isEqualTo(invalid);
            assertThat(failure.errorCode()).isEqualTo("NOT_FOUND");
        });
        verify(adminSanctionService).ban(eq(first), any(AdminUserSanctionRequest.class), eq(BATCH_ID));
        verify(adminSanctionService).ban(eq(third), any(AdminUserSanctionRequest.class), eq(BATCH_ID));
    }

    @Test
    void rejectsMoreThanTheServerSideLimitBeforeExecutingAnyItem() {
        List<UUID> tooMany = java.util.stream.IntStream.range(0, 501)
            .mapToObj(ignored -> UUID.randomUUID())
            .toList();

        assertThatThrownBy(() -> service.ban(new AdminBulkBanRequest(
            BATCH_ID, tooMany, "Repeated abuse confirmed")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue(
                "errorCode", AdminErrorCode.BULK_LIMIT_EXCEEDED);
        verify(adminSanctionService, never())
            .ban(any(UUID.class), any(AdminUserSanctionRequest.class), any(UUID.class));
    }

    @Test
    void replaysARecentBatchWithoutExecutingItAgain() {
        UUID first = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        AdminAuditLog success = auditRow(first, AuditStatus.SUCCESS, null);
        AdminAuditLog failure = auditRow(failed, AuditStatus.FAILURE, "NOT_FOUND");
        when(adminAuditLogRepository.findByBatchId(BATCH_ID)).thenReturn(List.of(success, failure));

        var response = service.ban(new AdminBulkBanRequest(
            BATCH_ID,
            List.of(first, failed),
            "Repeated abuse confirmed"));

        assertThat(response.requested()).isEqualTo(2);
        assertThat(response.succeeded()).isEqualTo(1);
        assertThat(response.failed()).singleElement()
            .extracting(
                AdminUserBulkServiceTest::failedId,
                AdminUserBulkServiceTest::failedCode)
            .containsExactly(failed, "NOT_FOUND");
        verify(adminSanctionService, never())
            .ban(any(UUID.class), any(AdminUserSanctionRequest.class), any(UUID.class));
    }

    @Test
    void notificationBulkActionReportsIneligibleUsersIndividually() {
        UUID activeId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();
        User active = user(activeId, AccountStatus.ACTIVE);
        User pending = user(pendingId, AccountStatus.PENDING);
        NotificationTemplate template = NotificationTemplate.builder()
            .templateKey("ADMIN_NOTICE")
            .titleEn("Notice")
            .messageEn("Please read this notice")
            .build();
        when(notificationTemplateRepository.findByTemplateKey("ADMIN_NOTICE"))
            .thenReturn(Optional.of(template));
        when(userRepository.findById(activeId)).thenReturn(Optional.of(active));
        when(userRepository.findById(pendingId)).thenReturn(Optional.of(pending));

        var response = service.notify(new AdminBulkNotifyRequest(
            BATCH_ID,
            List.of(activeId, pendingId),
            "ADMIN_NOTICE",
            java.util.Map.of(),
            "Important service notice"));

        assertThat(response.succeeded()).isEqualTo(1);
        assertThat(response.failed()).singleElement()
            .extracting(
                AdminUserBulkServiceTest::failedId,
                AdminUserBulkServiceTest::failedCode)
            .containsExactly(pendingId, AdminErrorCode.ADMIN_NOTIFICATION_INVALID_RECIPIENTS);
        verify(notificationEmitter).emit(any(NotificationRequest.class));
    }

    @Test
    void badgeBulkActionAssignsTheBadgeAndAuditsEachSuccess() {
        UUID userId = UUID.randomUUID();
        UUID badgeId = UUID.randomUUID();
        User user = user(userId, AccountStatus.ACTIVE);
        Badge badge = Badge.builder().nameEn("Helpful").build();
        ReflectionTestUtils.setField(badge, "id", badgeId);
        when(badgeRepository.findById(badgeId)).thenReturn(Optional.of(badge));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBadgeRepository.existsByUserIdAndBadgeId(userId, badgeId)).thenReturn(false);

        var response = service.assignBadge(new AdminBulkBadgeRequest(
            BATCH_ID,
            List.of(userId),
            badgeId,
            "Manual recognition approved"));

        assertThat(response.succeeded()).isEqualTo(1);
        verify(userBadgeRepository).saveAndFlush(any());
        ArgumentCaptor<AdminAuditEvent> audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(adminAuditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("USER_BADGE_ASSIGN");
        assertThat(audit.getValue().batchId()).isEqualTo(BATCH_ID);
        assertThat(audit.getValue().targetId()).isEqualTo(userId);
    }

    private AdminAuditLog auditRow(UUID userId, AuditStatus status, String errorCode) {
        AdminAuditLog row = org.mockito.Mockito.mock(AdminAuditLog.class);
        when(row.getTargetType()).thenReturn("USER");
        when(row.getTargetId()).thenReturn(userId);
        when(row.getCreatedAt()).thenReturn(NOW.minusSeconds(60));
        when(row.getStatus()).thenReturn(status);
        if (status == AuditStatus.FAILURE) {
            when(row.getErrorCode()).thenReturn(errorCode);
        }
        return row;
    }

    private User user(UUID id, AccountStatus status) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(id + "@example.com");
        user.setUsername("bulk_" + id.toString().replace('-', '_'));
        user.setFullName("Bulk User");
        user.setAccountStatus(status);
        return user;
    }

    private static UUID failedId(AdminBulkActionResponse.FailedUser failure) {
        return failure.id();
    }

    private static String failedCode(AdminBulkActionResponse.FailedUser failure) {
        return failure.errorCode();
    }
}
