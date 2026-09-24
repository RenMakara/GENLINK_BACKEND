package kh.edu.acleda.campuslink.modules.admin.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
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
import kh.edu.acleda.campuslink.modules.entertainment.entity.UserBadge;
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
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Partial-success, audited bulk actions for the Admin user directory (ADM-086). */
@Service
public class AdminUserBulkService {

    static final int MAX_USER_IDS = 500;
    static final Duration BATCH_DEDUPE_WINDOW = Duration.ofHours(24);

    private static final String TARGET_TYPE_USER = "USER";
    private static final String TARGET_TYPE_BULK = "BULK";
    private static final String ACTION_NOTIFY = "NOTIFICATION_BULK_SENT";
    private static final String ACTION_BADGE = "USER_BADGE_ASSIGN";
    private static final String BULK_NOTIFY_TYPE = "ADMIN_BULK_NOTIFY";
    private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    private final AdminSanctionService adminSanctionService;
    private final UserRepository userRepository;
    private final NotificationTemplateRepository notificationTemplateRepository;
    private final NotificationTemplateRenderer notificationTemplateRenderer;
    private final NotificationEmitter notificationEmitter;
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final AdminAuditService adminAuditService;
    private final Clock clock;
    private final ConcurrentMap<UUID, Object> batchLocks = new ConcurrentHashMap<>();

    @Autowired
    public AdminUserBulkService(
        AdminSanctionService adminSanctionService,
        UserRepository userRepository,
        NotificationTemplateRepository notificationTemplateRepository,
        NotificationTemplateRenderer notificationTemplateRenderer,
        NotificationEmitter notificationEmitter,
        BadgeRepository badgeRepository,
        UserBadgeRepository userBadgeRepository,
        AdminAuditLogRepository adminAuditLogRepository,
        AdminAuditService adminAuditService) {
        this(adminSanctionService, userRepository, notificationTemplateRepository,
            notificationTemplateRenderer, notificationEmitter, badgeRepository,
            userBadgeRepository, adminAuditLogRepository, adminAuditService, Clock.systemUTC());
    }

    AdminUserBulkService(
        AdminSanctionService adminSanctionService,
        UserRepository userRepository,
        NotificationTemplateRepository notificationTemplateRepository,
        NotificationTemplateRenderer notificationTemplateRenderer,
        NotificationEmitter notificationEmitter,
        BadgeRepository badgeRepository,
        UserBadgeRepository userBadgeRepository,
        AdminAuditLogRepository adminAuditLogRepository,
        AdminAuditService adminAuditService,
        Clock clock) {
        this.adminSanctionService = adminSanctionService;
        this.userRepository = userRepository;
        this.notificationTemplateRepository = notificationTemplateRepository;
        this.notificationTemplateRenderer = notificationTemplateRenderer;
        this.notificationEmitter = notificationEmitter;
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.adminAuditService = adminAuditService;
        this.clock = clock;
    }

    public AdminBulkActionResponse ban(AdminBulkBanRequest request) {
        requireRequest(request, "Bulk ban request is required");
        validateShape(request.batchId(), request.userIds());
        String reason = requireReason(request.reason(), 2000);
        return execute(request.batchId(), request.userIds(),
            (userId, batchId) -> banOne(userId, batchId, reason));
    }

    public AdminBulkActionResponse notify(AdminBulkNotifyRequest request) {
        requireRequest(request, "Bulk notification request is required");
        validateShape(request.batchId(), request.userIds());
        AdminBulkActionResponse replay = replayIfRecent(request.batchId());
        if (replay != null) {
            return replay;
        }

        String reason = requireReason(request.reason(), 500);
        NotificationTemplate template = requireTemplate(request.batchId(), request.templateKey(), reason);
        Map<String, String> parameters = validateNotification(
            request.batchId(), template, request.templateKey(), request.parameters(), reason);
        return execute(request.batchId(), request.userIds(),
            (userId, batchId) -> notifyOne(
                userId, batchId, request.templateKey(), parameters, template, reason));
    }

    public AdminBulkActionResponse assignBadge(AdminBulkBadgeRequest request) {
        requireRequest(request, "Bulk badge request is required");
        validateShape(request.batchId(), request.userIds());
        AdminBulkActionResponse replay = replayIfRecent(request.batchId());
        if (replay != null) {
            return replay;
        }

        String reason = requireReason(request.reason(), 2000);
        Badge badge = badgeRepository.findById(request.badgeId())
            .filter(candidate -> candidate.getDeletedAt() == null)
            .orElseThrow(() -> requestFailure(
                ACTION_BADGE, request.batchId(), reason, NOT_FOUND, "Badge not found"));
        return execute(request.batchId(), request.userIds(),
            (userId, batchId) -> assignBadgeOne(userId, batchId, badge, reason));
    }

    private String banOne(UUID userId, UUID batchId, String reason) {
        try {
            adminSanctionService.ban(
                userId, new AdminUserSanctionRequest(reason), batchId);
            return null;
        } catch (ApiException exception) {
            return errorCode(exception);
        } catch (RuntimeException exception) {
            return INTERNAL_SERVER_ERROR;
        }
    }

    private String notifyOne(
        UUID userId,
        UUID batchId,
        String templateKey,
        Map<String, String> parameters,
        NotificationTemplate template,
        String reason) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return failure(ACTION_NOTIFY, userId, batchId, reason, NOT_FOUND);
            }
            if (!isNotificationEligible(user)) {
                return failure(
                    ACTION_NOTIFY,
                    userId,
                    batchId,
                    reason,
                    AdminErrorCode.ADMIN_NOTIFICATION_INVALID_RECIPIENTS);
            }

            notificationEmitter.emit(new NotificationRequest(
                userId,
                templateKey,
                BULK_NOTIFY_TYPE,
                parameters,
                null,
                null,
                "admin-bulk-notify:" + batchId + ":" + userId));
            recordSuccess(
                ACTION_NOTIFY,
                userId,
                batchId,
                reason,
                new NotificationSnapshot(template.getTemplateKey()));
            return null;
        } catch (ApiException exception) {
            return failure(ACTION_NOTIFY, userId, batchId, reason, errorCode(exception));
        } catch (RuntimeException exception) {
            return failure(ACTION_NOTIFY, userId, batchId, reason, INTERNAL_SERVER_ERROR);
        }
    }

    private String assignBadgeOne(UUID userId, UUID batchId, Badge badge, String reason) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return failure(ACTION_BADGE, userId, batchId, reason, NOT_FOUND);
            }
            if (user.getAccountStatus() == AccountStatus.DELETED || user.getDeletedAt() != null) {
                return failure(
                    ACTION_BADGE, userId, batchId, reason, AdminErrorCode.USER_ANONYMISED);
            }

            boolean alreadyAssigned = userBadgeRepository.existsByUserIdAndBadgeId(
                userId, badge.getId());
            if (!alreadyAssigned) {
                try {
                    userBadgeRepository.saveAndFlush(UserBadge.builder()
                        .user(user)
                        .badge(badge)
                        .unlockedAt(LocalDateTime.now(clock))
                        .build());
                } catch (DataIntegrityViolationException duplicateAssignment) {
                    if (!userBadgeRepository.existsByUserIdAndBadgeId(userId, badge.getId())) {
                        return failure(ACTION_BADGE, userId, batchId, reason, INTERNAL_SERVER_ERROR);
                    }
                    alreadyAssigned = true;
                }
            }

            recordSuccess(
                ACTION_BADGE,
                userId,
                batchId,
                reason,
                new BadgeSnapshot(badge.getId(), !alreadyAssigned));
            return null;
        } catch (ApiException exception) {
            return failure(ACTION_BADGE, userId, batchId, reason, errorCode(exception));
        } catch (RuntimeException exception) {
            return failure(ACTION_BADGE, userId, batchId, reason, INTERNAL_SERVER_ERROR);
        }
    }

    private AdminBulkActionResponse execute(
        UUID batchId,
        List<UUID> userIds,
        BiFunction<UUID, UUID, String> action) {
        validateShape(batchId, userIds);
        AdminBulkActionResponse replay = replayIfRecent(batchId);
        if (replay != null) {
            return replay;
        }

        Object lock = batchLocks.computeIfAbsent(batchId, ignored -> new Object());
        try {
            synchronized (lock) {
                replay = replayIfRecent(batchId);
                if (replay != null) {
                    return replay;
                }

                List<AdminBulkActionResponse.FailedUser> failed = new ArrayList<>();
                int succeeded = 0;
                for (UUID userId : userIds) {
                    String errorCode;
                    try {
                        errorCode = action.apply(userId, batchId);
                    } catch (RuntimeException exception) {
                        errorCode = INTERNAL_SERVER_ERROR;
                    }
                    if (errorCode == null) {
                        succeeded++;
                    } else {
                        failed.add(new AdminBulkActionResponse.FailedUser(userId, errorCode));
                    }
                }
                return new AdminBulkActionResponse(batchId, userIds.size(), succeeded, failed);
            }
        } finally {
            batchLocks.remove(batchId, lock);
        }
    }

    private AdminBulkActionResponse replayIfRecent(UUID batchId) {
        Instant cutoff = clock.instant().minus(BATCH_DEDUPE_WINDOW);
        List<AdminAuditLog> rows = adminAuditLogRepository.findByBatchId(batchId);
        if (rows == null || rows.isEmpty()) {
            return null;
        }

        Map<UUID, AdminAuditLog> latestByUser = new LinkedHashMap<>();
        rows.stream()
            .filter(row -> TARGET_TYPE_USER.equals(row.getTargetType()))
            .filter(row -> row.getTargetId() != null)
            .filter(row -> row.getCreatedAt() != null && !row.getCreatedAt().isBefore(cutoff))
            .sorted(Comparator.comparing(AdminAuditLog::getCreatedAt))
            .forEach(row -> latestByUser.put(row.getTargetId(), row));
        if (latestByUser.isEmpty()) {
            return null;
        }

        List<AdminBulkActionResponse.FailedUser> failed = latestByUser.values().stream()
            .filter(row -> row.getStatus() == AuditStatus.FAILURE)
            .map(row -> new AdminBulkActionResponse.FailedUser(
                row.getTargetId(), row.getErrorCode() == null ? INTERNAL_SERVER_ERROR : row.getErrorCode()))
            .toList();
        return new AdminBulkActionResponse(
            batchId,
            latestByUser.size(),
            latestByUser.size() - failed.size(),
            failed);
    }

    private NotificationTemplate requireTemplate(UUID batchId, String templateKey, String reason) {
        return notificationTemplateRepository.findByTemplateKey(templateKey)
            .orElseThrow(() -> requestFailure(
                ACTION_NOTIFY,
                batchId,
                reason,
                AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_NOT_FOUND,
                "Admin notification template not found"));
    }

    private Map<String, String> validateNotification(
        UUID batchId,
        NotificationTemplate template,
        String templateKey,
        Map<String, String> parameters,
        String reason) {
        try {
            Map<String, String> safeParameters = parameters == null ? Map.of() : Map.copyOf(parameters);
            new NotificationRequest(UUID.randomUUID(), templateKey, BULK_NOTIFY_TYPE, safeParameters);
            notificationTemplateRenderer.render(template, Language.ENGLISH, safeParameters);
            notificationTemplateRenderer.render(template, Language.KHMER, safeParameters);
            return safeParameters;
        } catch (RuntimeException exception) {
            throw requestFailure(
                ACTION_NOTIFY,
                batchId,
                reason,
                AdminErrorCode.ADMIN_NOTIFICATION_TEMPLATE_INVALID,
                "Bulk notification template or parameters are invalid: " + exception.getMessage());
        }
    }

    private String failure(
        String action,
        UUID userId,
        UUID batchId,
        String reason,
        String errorCode) {
        adminAuditService.recordFailure(
            action, TARGET_TYPE_USER, userId, reason, errorCode, batchId);
        return errorCode;
    }

    private void recordSuccess(
        String action,
        UUID userId,
        UUID batchId,
        String reason,
        Object snapshot) {
        adminAuditService.record(AdminAuditEvent.builder()
            .action(action)
            .targetType(TARGET_TYPE_USER)
            .targetId(userId)
            .batchId(batchId)
            .reason(reason)
            .afterJson(adminAuditService.toJson(snapshot))
            .status(AuditStatus.SUCCESS)
            .build());
    }

    private ApiException requestFailure(
        String action,
        UUID batchId,
        String reason,
        String errorCode,
        String message) {
        adminAuditService.recordFailure(
            action, TARGET_TYPE_BULK, batchId, reason, errorCode, batchId);
        return new ApiException(message, errorCode);
    }

    private static void requireRequest(Object request, String message) {
        if (request == null) {
            throw new ApiException(message, VALIDATION_ERROR);
        }
    }

    private static void validateShape(UUID batchId, List<UUID> userIds) {
        if (batchId == null) {
            throw new ApiException("batchId is required", VALIDATION_ERROR);
        }
        if (userIds == null || userIds.isEmpty()) {
            throw new ApiException("userIds must contain at least one id", VALIDATION_ERROR);
        }
        if (userIds.size() > MAX_USER_IDS) {
            throw new ApiException(
                "A bulk request may contain at most " + MAX_USER_IDS + " user ids",
                AdminErrorCode.BULK_LIMIT_EXCEEDED);
        }
        if (userIds.stream().anyMatch(Objects::isNull)) {
            throw new ApiException("userIds must not contain null values", VALIDATION_ERROR);
        }
        if (new LinkedHashSet<>(userIds).size() != userIds.size()) {
            throw new ApiException("userIds must not contain duplicates", VALIDATION_ERROR);
        }
    }

    private static String requireReason(String reason, int maxLength) {
        if (reason == null) {
            throw new ApiException("reason is required", VALIDATION_ERROR);
        }
        String trimmed = reason.trim();
        if (trimmed.length() < 10 || trimmed.length() > maxLength) {
            throw new ApiException(
                "reason must contain between 10 and " + maxLength + " characters",
                VALIDATION_ERROR);
        }
        return trimmed;
    }

    private static boolean isNotificationEligible(User user) {
        return user.getAccountStatus() == AccountStatus.ACTIVE && user.getDeletedAt() == null;
    }

    private static String errorCode(ApiException exception) {
        if (exception instanceof NotFoundException) {
            return NOT_FOUND;
        }
        return exception.getErrorCode() == null || exception.getErrorCode().isBlank()
            ? INTERNAL_SERVER_ERROR : exception.getErrorCode();
    }

    private record NotificationSnapshot(String templateKey) {
    }

    private record BadgeSnapshot(UUID badgeId, boolean newlyAssigned) {
    }
}
