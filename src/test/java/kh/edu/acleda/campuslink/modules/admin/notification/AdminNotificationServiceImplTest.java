package kh.edu.acleda.campuslink.modules.admin.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminBroadcastAudience;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminBroadcastRequest;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminNotificationTemplateCreateRequest;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminNotificationTemplateUpdateRequest;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.response.AdminBroadcastResponse;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationTemplate;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationTemplateRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTemplateRenderer;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceImplTest {

    @Mock
    private NotificationTemplateRepository templateRepository;
    @Mock
    private NotificationEmitter notificationEmitter;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminAuditService auditService;

    private AdminNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminNotificationServiceImpl(
            templateRepository,
            new NotificationTemplateRenderer(),
            notificationEmitter,
            userRepository,
            auditService);
    }

    @Test
    void selectedBroadcastEmitsOneBoundedRequestPerActiveRecipient() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        User first = activeUser(firstId);
        User second = activeUser(secondId);
        when(templateRepository.findByTemplateKey("ADMIN_MAINTENANCE"))
            .thenReturn(java.util.Optional.of(template("ADMIN_MAINTENANCE")));
        when(userRepository.findAllById(any()))
            .thenReturn(List.of(first, second));

        AdminBroadcastResponse response = service.broadcast(new AdminBroadcastRequest(
            "ADMIN_MAINTENANCE",
            AdminBroadcastAudience.SELECTED,
            List.of(firstId, secondId),
            Map.of("window", "22:00"),
            "planned maintenance"));

        assertThat(response.recipientCount()).isEqualTo(2);
        assertThat(response.maxRecipients())
            .isEqualTo(AdminNotificationServiceImpl.MAX_BROADCAST_RECIPIENTS);

        ArgumentCaptor<NotificationRequest> requests = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter, times(2)).emit(requests.capture());
        assertThat(requests.getAllValues())
            .allSatisfy(request -> {
                assertThat(request.templateKey()).isEqualTo("ADMIN_MAINTENANCE");
                assertThat(request.type()).isEqualTo("ADMIN_BROADCAST");
                assertThat(request.parameters()).containsEntry("window", "22:00");
                assertThat(request.dedupeKey()).startsWith("admin-broadcast:");
            });
        assertThat(requests.getAllValues().get(0).dedupeKey())
            .isEqualTo(requests.getAllValues().get(1).dedupeKey());

        ArgumentCaptor<AdminAuditEvent> auditEvents = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(auditService).record(auditEvents.capture());
        assertThat(auditEvents.getValue().action()).isEqualTo("NOTIFICATION_BROADCAST_SENT");
        assertThat(auditEvents.getValue().batchId()).isEqualTo(response.batchId());
    }

    @Test
    void allActiveAudienceStopsAtTheServerRecipientLimit() {
        when(templateRepository.findByTemplateKey("ADMIN_NOTICE"))
            .thenReturn(java.util.Optional.of(template("ADMIN_NOTICE")));
        List<User> tooManyUsers = new ArrayList<>();
        for (int i = 0; i <= AdminNotificationServiceImpl.MAX_BROADCAST_RECIPIENTS; i++) {
            tooManyUsers.add(org.mockito.Mockito.mock(User.class));
        }
        when(userRepository.findByAccountStatusAndDeletedAtIsNullOrderById(
            eq(AccountStatus.ACTIVE), any())).thenReturn(tooManyUsers);

        assertThatThrownBy(() -> service.broadcast(new AdminBroadcastRequest(
            "ADMIN_NOTICE", AdminBroadcastAudience.ALL_ACTIVE, null,
            Map.of("window", "22:00"), "service notice")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue(
                "errorCode", AdminErrorCode.ADMIN_NOTIFICATION_RECIPIENT_LIMIT);

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
        verify(auditService).recordFailure(
            eq("NOTIFICATION_BROADCAST_SENT"), eq("NOTIFICATION_BROADCAST"), any(UUID.class),
            eq("service notice"), eq(AdminErrorCode.ADMIN_NOTIFICATION_RECIPIENT_LIMIT));
    }

    @Test
    void selectedAudienceRejectsInactiveUsersInsteadOfPartiallySending() {
        UUID userId = UUID.randomUUID();
        User pending = activeUser(userId);
        when(pending.getAccountStatus()).thenReturn(AccountStatus.PENDING);
        when(templateRepository.findByTemplateKey("ADMIN_NOTICE"))
            .thenReturn(java.util.Optional.of(template("ADMIN_NOTICE")));
        when(userRepository.findAllById(any())).thenReturn(List.of(pending));

        assertThatThrownBy(() -> service.broadcast(new AdminBroadcastRequest(
            "ADMIN_NOTICE", AdminBroadcastAudience.SELECTED, List.of(userId),
            Map.of("window", "22:00"), "no partial send")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue(
                "errorCode", AdminErrorCode.ADMIN_NOTIFICATION_INVALID_RECIPIENTS);

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }

    @Test
    void templateCreateIsLimitedToAdminNamespaceAndAudited() {
        when(templateRepository.existsByTemplateKey("ADMIN_WELCOME"))
            .thenReturn(false);
        when(templateRepository.save(any(NotificationTemplate.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createTemplate(new AdminNotificationTemplateCreateRequest(
            "ADMIN_WELCOME", "Welcome", "ស្វាគមន៍", "Hello {name}", "សួស្តី {name}", "content review"));

        assertThat(response.templateKey()).isEqualTo("ADMIN_WELCOME");
        verify(templateRepository).save(any(NotificationTemplate.class));
        verify(auditService).record(any(AdminAuditEvent.class));

        assertThatThrownBy(() -> service.createTemplate(new AdminNotificationTemplateCreateRequest(
            "SECURITY_SUSPICIOUS_LOGIN", "Unsafe", null, "Do not use", null, "must be rejected")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue(
                "errorCode", AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_INVALID);
    }

    @Test
    void templateCreateRejectsDifferentLocalizedPlaceholdersBeforeSave() {
        assertThatThrownBy(() -> service.createTemplate(new AdminNotificationTemplateCreateRequest(
            "ADMIN_BAD_TRANSLATION", "Hello {name}", "សួស្តី {user}",
            "Body {name}", "ខ្លឹមសារ {user}", "review placeholders")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue(
                "errorCode", AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_INVALID);

        verify(templateRepository, never()).save(any(NotificationTemplate.class));
    }

    @Test
    void templateUpdateChangesLocalizedTextWithoutChangingTheKey() {
        NotificationTemplate existing = template("ADMIN_MAINTENANCE");
        when(templateRepository.findByTemplateKey("ADMIN_MAINTENANCE"))
            .thenReturn(java.util.Optional.of(existing));
        when(templateRepository.save(any(NotificationTemplate.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateTemplate("ADMIN_MAINTENANCE", new AdminNotificationTemplateUpdateRequest(
            "Updated notice", "បានធ្វើបច្ចុប្បន្នភាព", "Updated at {window}",
            "បានធ្វើបច្ចុប្បន្នភាពនៅ {window}", "copy review"));

        assertThat(response.templateKey()).isEqualTo("ADMIN_MAINTENANCE");
        assertThat(response.titleEn()).isEqualTo("Updated notice");
        assertThat(existing.getTemplateKey()).isEqualTo("ADMIN_MAINTENANCE");
        verify(templateRepository).save(existing);
        verify(auditService).record(any(AdminAuditEvent.class));
    }

    private NotificationTemplate template(String key) {
        return NotificationTemplate.builder()
            .templateKey(key)
            .titleEn("Notice")
            .titleKm("សេចក្តីជូនដំណឹង")
            .messageEn("Maintenance window: {window}")
            .messageKm("ពេលថែទាំ៖ {window}")
            .build();
    }

    private User activeUser(UUID id) {
        User user = org.mockito.Mockito.mock(User.class);
        // This helper is shared by tests that may stop before recipient eligibility is read.
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        lenient().when(user.getDeletedAt()).thenReturn(null);
        return user;
    }
}
