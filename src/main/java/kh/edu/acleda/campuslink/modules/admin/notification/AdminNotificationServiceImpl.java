package kh.edu.acleda.campuslink.modules.admin.notification;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminBroadcastAudience;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminBroadcastRequest;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminNotificationTemplateCreateRequest;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminNotificationTemplateUpdateRequest;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.response.AdminBroadcastResponse;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.response.AdminNotificationTemplateResponse;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationTemplate;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationTemplateRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTemplateRenderer;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements the bounded and audited Admin notification surface. */
@Service
public class AdminNotificationServiceImpl implements AdminNotificationService {

    static final int MAX_BROADCAST_RECIPIENTS = 500;
    static final String ADMIN_TEMPLATE_PREFIX = "ADMIN_";
    static final String BROADCAST_TYPE = "ADMIN_BROADCAST";

    private static final String TEMPLATE_TARGET_TYPE = "NOTIFICATION_TEMPLATE";
    private static final String BROADCAST_TARGET_TYPE = "NOTIFICATION_BROADCAST";
    private static final String CREATE_TEMPLATE_ACTION = "NOTIFICATION_TEMPLATE_CREATED";
    private static final String UPDATE_TEMPLATE_ACTION = "NOTIFICATION_TEMPLATE_UPDATED";
    private static final String BROADCAST_ACTION = "NOTIFICATION_BROADCAST_SENT";
    private static final String BROADCAST_DEDUPE_PREFIX = "admin-broadcast:";
    private static final int MAX_TITLE_LENGTH = 180;
    private static final int MAX_BODY_LENGTH = 4_000;
    private static final int MAX_PARAMETER_COUNT = 20;
    private static final Pattern MANAGED_TEMPLATE_KEY = Pattern.compile(
        "ADMIN_[A-Z0-9]+(?:[_.-][A-Z0-9]+)*");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");

    private final NotificationTemplateRepository templateRepository;
    private final NotificationTemplateRenderer templateRenderer;
    private final NotificationEmitter notificationEmitter;
    private final UserRepository userRepository;
    private final AdminAuditService auditService;

    public AdminNotificationServiceImpl(NotificationTemplateRepository templateRepository,
                                        NotificationTemplateRenderer templateRenderer,
                                        NotificationEmitter notificationEmitter,
                                        UserRepository userRepository,
                                        AdminAuditService auditService) {
        this.templateRepository = templateRepository;
        this.templateRenderer = templateRenderer;
        this.notificationEmitter = notificationEmitter;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminNotificationTemplateResponse> listTemplates() {
        return templateRepository
            .findByTemplateKeyStartingWithOrderByTemplateKeyAsc(ADMIN_TEMPLATE_PREFIX)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public AdminNotificationTemplateResponse createTemplate(
        AdminNotificationTemplateCreateRequest request) {
        if (request == null) {
            throw failure(CREATE_TEMPLATE_ACTION, null, null,
                AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_INVALID,
                "Template request is required");
        }

        String reason = request.reason();
        requireReason(CREATE_TEMPLATE_ACTION, null, reason);
        validateManagedTemplateKey(CREATE_TEMPLATE_ACTION, null, reason, request.templateKey());
        validateContent(CREATE_TEMPLATE_ACTION, null, reason,
            request.titleEn(), request.titleKm(), request.messageEn(), request.messageKm());

        if (templateRepository.existsByTemplateKey(request.templateKey())) {
            throw failure(CREATE_TEMPLATE_ACTION, null, reason,
                AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_DUPLICATE,
                "An admin notification template with this key already exists");
        }

        NotificationTemplate template = NotificationTemplate.builder()
            .templateKey(request.templateKey())
            .titleEn(request.titleEn())
            .titleKm(request.titleKm())
            .messageEn(request.messageEn())
            .messageKm(request.messageKm())
            .build();
        try {
            NotificationTemplate saved = templateRepository.save(template);
            NotificationTemplate result = saved == null ? template : saved;
            recordTemplateSuccess(CREATE_TEMPLATE_ACTION, result, null, reason);
            return toResponse(result);
        } catch (RuntimeException exception) {
            auditService.recordFailure(CREATE_TEMPLATE_ACTION, TEMPLATE_TARGET_TYPE, null,
                reason, AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_INVALID);
            throw exception;
        }
    }

    @Override
    @Transactional
    public AdminNotificationTemplateResponse updateTemplate(
        String templateKey, AdminNotificationTemplateUpdateRequest request) {
        if (request == null) {
            throw failure(UPDATE_TEMPLATE_ACTION, null, null,
                AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_INVALID,
                "Template request is required");
        }

        String reason = request.reason();
        requireReason(UPDATE_TEMPLATE_ACTION, null, reason);
        validateManagedTemplateKey(UPDATE_TEMPLATE_ACTION, null, reason, templateKey);

        NotificationTemplate template = templateRepository.findByTemplateKey(templateKey)
            .orElseThrow(() -> failure(UPDATE_TEMPLATE_ACTION, null, reason,
                AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_NOT_FOUND,
                "Admin notification template not found"));
        UUID templateId = template.getId();
        validateContent(UPDATE_TEMPLATE_ACTION, templateId, reason,
            request.titleEn(), request.titleKm(), request.messageEn(), request.messageKm());

        TemplateSnapshot before = snapshot(template);
        template.setTitleEn(request.titleEn());
        template.setTitleKm(request.titleKm());
        template.setMessageEn(request.messageEn());
        template.setMessageKm(request.messageKm());
        try {
            NotificationTemplate saved = templateRepository.save(template);
            NotificationTemplate result = saved == null ? template : saved;
            recordTemplateSuccess(UPDATE_TEMPLATE_ACTION, result, before, reason);
            return toResponse(result);
        } catch (RuntimeException exception) {
            auditService.recordFailure(UPDATE_TEMPLATE_ACTION, TEMPLATE_TARGET_TYPE, templateId,
                reason, AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_INVALID);
            throw exception;
        }
    }

    @Override
    @Transactional
    public AdminBroadcastResponse broadcast(AdminBroadcastRequest request) {
        UUID batchId = UUID.randomUUID();
        if (request == null) {
            throw failure(BROADCAST_ACTION, batchId, null,
                AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_INVALID,
                "Broadcast request is required");
        }

        String reason = request.reason();
        requireReason(BROADCAST_ACTION, batchId, reason);
        validateManagedTemplateKey(BROADCAST_ACTION, batchId, reason, request.templateKey());
        NotificationTemplate template = templateRepository.findByTemplateKey(request.templateKey())
            .orElseThrow(() -> failure(BROADCAST_ACTION, batchId, reason,
                AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_NOT_FOUND,
                "Admin notification template not found"));
        validateBroadcastPayload(template, request.templateKey(), request.parameters(), batchId, reason);

        try {
            List<User> recipients = resolveRecipients(request, batchId, reason);
            String dedupeKey = BROADCAST_DEDUPE_PREFIX + batchId;
            Map<String, String> parameters = request.parameters() == null
                ? Map.of() : request.parameters();
            for (User recipient : recipients) {
                notificationEmitter.emit(new NotificationRequest(
                    recipient.getId(), request.templateKey(), BROADCAST_TYPE,
                    parameters, null, null, dedupeKey));
            }

            BroadcastSnapshot snapshot = new BroadcastSnapshot(
                batchId, request.templateKey(), request.audience(), recipients.size());
            auditService.record(AdminAuditEvent.builder()
                .action(BROADCAST_ACTION)
                .targetType(BROADCAST_TARGET_TYPE)
                .targetId(batchId)
                .reason(reason)
                .batchId(batchId)
                .afterJson(auditService.toJson(snapshot))
                .status(AuditStatus.SUCCESS)
                .build());
            return new AdminBroadcastResponse(batchId, request.templateKey(), request.audience(),
                recipients.size(), MAX_BROADCAST_RECIPIENTS);
        } catch (ApiException exception) {
            // The failure helper already wrote the independent audit row.
            throw exception;
        } catch (RuntimeException exception) {
            auditService.recordFailure(BROADCAST_ACTION, BROADCAST_TARGET_TYPE, batchId,
                reason, "ADMIN_NOTIFICATION_BROADCAST_FAILED");
            throw exception;
        }
    }

    private List<User> resolveRecipients(AdminBroadcastRequest request,
                                         UUID batchId,
                                         String reason) {
        if (request.audience() == null) {
            throw failure(BROADCAST_ACTION, batchId, reason,
                AdminErrorCode.ADMIN_NOTIFICATION_NO_RECIPIENTS,
                "Broadcast audience is required");
        }

        if (request.audience() == AdminBroadcastAudience.ALL_ACTIVE) {
            if (request.recipientUserIds() != null && !request.recipientUserIds().isEmpty()) {
                throw failure(BROADCAST_ACTION, batchId, reason,
                    AdminErrorCode.ADMIN_NOTIFICATION_INVALID_RECIPIENTS,
                    "ALL_ACTIVE broadcasts must not include selected recipient ids");
            }
            List<User> recipients = userRepository
                .findByAccountStatusAndDeletedAtIsNullOrderById(
                    AccountStatus.ACTIVE, PageRequest.of(0, MAX_BROADCAST_RECIPIENTS + 1));
            if (recipients == null || recipients.isEmpty()) {
                throw failure(BROADCAST_ACTION, batchId, reason,
                    AdminErrorCode.ADMIN_NOTIFICATION_NO_RECIPIENTS,
                    "No active users are eligible for this broadcast");
            }
            if (recipients.size() > MAX_BROADCAST_RECIPIENTS) {
                throw failure(BROADCAST_ACTION, batchId, reason,
                    AdminErrorCode.ADMIN_NOTIFICATION_RECIPIENT_LIMIT,
                    "Broadcast recipient limit is " + MAX_BROADCAST_RECIPIENTS);
            }
            return recipients;
        }

        List<UUID> requestedIds = request.recipientUserIds();
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw failure(BROADCAST_ACTION, batchId, reason,
                AdminErrorCode.ADMIN_NOTIFICATION_NO_RECIPIENTS,
                "Selected broadcast requires at least one recipient");
        }
        if (requestedIds.size() > MAX_BROADCAST_RECIPIENTS) {
            throw failure(BROADCAST_ACTION, batchId, reason,
                AdminErrorCode.ADMIN_NOTIFICATION_RECIPIENT_LIMIT,
                "Broadcast recipient limit is " + MAX_BROADCAST_RECIPIENTS);
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size() || uniqueIds.contains(null)) {
            throw failure(BROADCAST_ACTION, batchId, reason,
                AdminErrorCode.ADMIN_NOTIFICATION_INVALID_RECIPIENTS,
                "Selected recipients must be unique and non-null");
        }

        List<User> recipients = userRepository.findAllById(uniqueIds);
        Set<UUID> foundIds = recipients.stream()
            .map(User::getId)
            .collect(java.util.stream.Collectors.toSet());
        boolean allEligible = recipients.size() == uniqueIds.size()
            && foundIds.equals(uniqueIds)
            && recipients.stream().allMatch(this::isEligible);
        if (!allEligible) {
            throw failure(BROADCAST_ACTION, batchId, reason,
                AdminErrorCode.ADMIN_NOTIFICATION_INVALID_RECIPIENTS,
                "Every selected recipient must be an active, non-deleted user");
        }
        return recipients.stream()
            .sorted(Comparator.comparing(User::getId))
            .toList();
    }

    private boolean isEligible(User user) {
        return user != null
            && user.getId() != null
            && user.getAccountStatus() == AccountStatus.ACTIVE
            && user.getDeletedAt() == null;
    }

    private void validateBroadcastPayload(NotificationTemplate template,
                                           String templateKey,
                                           Map<String, String> parameters,
                                           UUID batchId,
                                           String reason) {
        Map<String, String> safeParameters = parameters == null ? Map.of() : parameters;
        try {
            new NotificationRequest(UUID.randomUUID(), templateKey, BROADCAST_TYPE, safeParameters);
            if (safeParameters.size() > MAX_PARAMETER_COUNT) {
                throw new IllegalArgumentException("Too many notification parameters");
            }
            // Both paths are checked before any event is published. A Khmer user must not be
            // the first one to discover a missing placeholder in an admin-authored template.
            templateRenderer.render(template, Language.ENGLISH, safeParameters);
            templateRenderer.render(template, Language.KHMER, safeParameters);
        } catch (IllegalArgumentException exception) {
            throw failure(BROADCAST_ACTION, batchId, reason,
                AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_INVALID,
                "Broadcast template or parameters are invalid: " + exception.getMessage());
        }
    }

    private void validateManagedTemplateKey(String action,
                                            UUID targetId,
                                            String reason,
                                            String templateKey) {
        if (templateKey == null || templateKey.length() > 120
            || !MANAGED_TEMPLATE_KEY.matcher(templateKey).matches()) {
            throw failure(action, targetId, reason,
                AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_INVALID,
                "Template key must match the ADMIN_ namespace format");
        }
    }

    private void validateContent(String action,
                                 UUID targetId,
                                 String reason,
                                 String titleEn,
                                 String titleKm,
                                 String messageEn,
                                 String messageKm) {
        try {
            requireText(titleEn, "English title");
            requireText(messageEn, "English message");
            if (titleEn.length() > MAX_TITLE_LENGTH || (titleKm != null && titleKm.length() > MAX_TITLE_LENGTH)) {
                throw new IllegalArgumentException("Notification title is too long");
            }
            if (messageEn.length() > MAX_BODY_LENGTH || (messageKm != null && messageKm.length() > MAX_BODY_LENGTH)) {
                throw new IllegalArgumentException("Notification message is too long");
            }

            Set<String> englishPlaceholders = placeholders(titleEn, messageEn);
            if (englishPlaceholders.size() > MAX_PARAMETER_COUNT) {
                throw new IllegalArgumentException("Notification template has too many placeholders");
            }
            Set<String> khmerPlaceholders = placeholders(titleKm, messageKm);
            boolean hasKhmerContent = hasText(titleKm) || hasText(messageKm);
            if (hasKhmerContent && !khmerPlaceholders.equals(englishPlaceholders)) {
                throw new IllegalArgumentException(
                    "Khmer and English templates must use the same placeholders");
            }
        } catch (IllegalArgumentException exception) {
            throw failure(action, targetId, reason,
                AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_INVALID, exception.getMessage());
        }
    }

    private Set<String> placeholders(String... values) {
        Set<String> names = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            Matcher matcher = PLACEHOLDER.matcher(value);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private void requireReason(String action, UUID targetId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw failure(action, targetId, reason,
                AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_INVALID,
                "An audit reason is required");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void recordTemplateSuccess(String action,
                                       NotificationTemplate template,
                                       TemplateSnapshot before,
                                       String reason) {
        auditService.record(AdminAuditEvent.builder()
            .action(action)
            .targetType(TEMPLATE_TARGET_TYPE)
            .targetId(template.getId())
            .beforeJson(auditService.toJson(before))
            .afterJson(auditService.toJson(snapshot(template)))
            .reason(reason)
            .status(AuditStatus.SUCCESS)
            .build());
    }

    private ApiException failure(String action,
                                 UUID targetId,
                                 String reason,
                                 String errorCode,
                                 String message) {
        auditService.recordFailure(action, action.startsWith("NOTIFICATION_TEMPLATE")
                ? TEMPLATE_TARGET_TYPE : BROADCAST_TARGET_TYPE,
            targetId, reason, errorCode);
        return new ApiException(message, errorCode);
    }

    private TemplateSnapshot snapshot(NotificationTemplate template) {
        return new TemplateSnapshot(template.getId(), template.getTemplateKey(),
            template.getTitleEn(), template.getTitleKm(), template.getMessageEn(),
            template.getMessageKm());
    }

    private AdminNotificationTemplateResponse toResponse(NotificationTemplate template) {
        return new AdminNotificationTemplateResponse(template.getId(), template.getTemplateKey(),
            template.getTitleEn(), template.getTitleKm(), template.getMessageEn(),
            template.getMessageKm(), template.getCreatedAt(), template.getUpdatedAt());
    }

    private record TemplateSnapshot(UUID id,
                                    String templateKey,
                                    String titleEn,
                                    String titleKm,
                                    String messageEn,
                                    String messageKm) {
    }

    private record BroadcastSnapshot(UUID batchId,
                                     String templateKey,
                                     AdminBroadcastAudience audience,
                                     int recipientCount) {
    }
}
