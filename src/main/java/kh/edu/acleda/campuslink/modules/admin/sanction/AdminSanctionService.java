package kh.edu.acleda.campuslink.modules.admin.sanction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
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
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional user-enforcement operations for the Admin portal (ADM-077). */
@Service
public class AdminSanctionService {

    private static final String TARGET_TYPE_USER = "USER";
    private static final String ACTION_BAN = "USER_BAN";
    private static final String ACTION_UNBAN = "USER_UNBAN";
    private static final String ACTION_SUSPEND = "USER_SUSPEND";
    private static final String ACTION_WARNING = "USER_WARNING";
    private static final String ACTION_LIFT = "USER_SANCTION_LIFT";
    private static final String WARNING_DELIVERY_STATUS = "NOT_DELIVERED";
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    private static final Set<Integer> ALLOWED_SUSPENSION_HOURS = Set.of(24, 24 * 7, 24 * 30);

    private final UserRepository userRepository;
    private final UserSanctionRepository userSanctionRepository;
    private final AdminUserRepository adminUserRepository;
    private final RefreshTokenService refreshTokenService;
    private final AdminAuditService adminAuditService;
    private final Clock clock;

    @Autowired
    public AdminSanctionService(
        UserRepository userRepository,
        UserSanctionRepository userSanctionRepository,
        AdminUserRepository adminUserRepository,
        RefreshTokenService refreshTokenService,
        AdminAuditService adminAuditService) {
        this(userRepository, userSanctionRepository, adminUserRepository, refreshTokenService,
            adminAuditService, Clock.systemUTC());
    }

    AdminSanctionService(
        UserRepository userRepository,
        UserSanctionRepository userSanctionRepository,
        AdminUserRepository adminUserRepository,
        RefreshTokenService refreshTokenService,
        AdminAuditService adminAuditService,
        Clock clock) {
        this.userRepository = userRepository;
        this.userSanctionRepository = userSanctionRepository;
        this.adminUserRepository = adminUserRepository;
        this.refreshTokenService = refreshTokenService;
        this.adminAuditService = adminAuditService;
        this.clock = clock;
    }

    /** Permanently bans the account until an authorised admin lifts the sanction. */
    @Transactional
    public void ban(UUID userId, AdminUserSanctionRequest request) {
        ban(userId, request, null);
    }

    /** Permanently bans the account and optionally attaches the action to a bulk batch. */
    @Transactional
    public void ban(UUID userId, AdminUserSanctionRequest request, UUID batchId) {
        String reason = requireReason(request);
        AdminUser actor = currentAdmin();

        try {
            User user = lockedUser(userId, ACTION_BAN, reason, batchId);
            ensureNotAnonymised(user, userId, ACTION_BAN, reason, batchId);

            UserSanction existingBan = userSanctionRepository
                .findOpenByUserIdAndType(userId, SanctionType.BAN)
                .orElse(null);
            if (user.getAccountStatus() == AccountStatus.BANNED || existingBan != null) {
                throw failure(ACTION_BAN, userId, reason,
                    AdminErrorCode.USER_ALREADY_BANNED, "User is already banned.", batchId);
            }

            Instant now = clock.instant();
            AccountStatus previousStatus = user.getAccountStatus();
            int beforeTokenVersion = user.getTokenVersion();
            UserSanction sanction = UserSanction.builder()
                .user(user)
                .adminUser(actor)
                .type(SanctionType.BAN)
                .reason(reason)
                .previousStatus(previousStatus)
                .startsAt(now)
                .expiresAt(null)
                .build();

            userSanctionRepository.save(sanction);
            user.setAccountStatus(AccountStatus.BANNED);
            user.incrementTokenVersion();
            refreshTokenService.revoke(user);
            userRepository.save(user);

            recordSuccess(ACTION_BAN, userId, reason,
                snapshot(previousStatus, previousStatus, beforeTokenVersion, null, null),
                snapshot(previousStatus, user.getAccountStatus(), user.getTokenVersion(),
                    SanctionType.BAN, null), batchId);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_BAN, userId, reason, INTERNAL_SERVER_ERROR, batchId);
            throw ex;
        }
    }

    /** Lifts the current ban and restores exactly the status captured when it was created. */
    @Transactional
    public void unban(UUID userId, AdminUserSanctionRequest request) {
        String reason = requireReason(request);
        AdminUser actor = currentAdmin();

        try {
            User user = lockedUser(userId, ACTION_UNBAN, reason);
            ensureNotAnonymised(user, userId, ACTION_UNBAN, reason);

            UserSanction ban = userSanctionRepository
                .findOpenByUserIdAndType(userId, SanctionType.BAN)
                .orElseThrow(() -> failure(ACTION_UNBAN, userId, reason,
                    AdminErrorCode.USER_NOT_BANNED, "User is not currently banned."));

            if (user.getAccountStatus() != AccountStatus.BANNED) {
                throw failure(ACTION_UNBAN, userId, reason,
                    AdminErrorCode.USER_NOT_BANNED, "User is not currently banned.");
            }

            AccountStatus restoredStatus = ban.getPreviousStatus();
            if (restoredStatus == null || restoredStatus == AccountStatus.BANNED
                || restoredStatus == AccountStatus.DELETED) {
                throw failure(ACTION_UNBAN, userId, reason, INTERNAL_SERVER_ERROR,
                    "The ban does not contain a valid previous account status.");
            }

            Instant now = clock.instant();
            int beforeTokenVersion = user.getTokenVersion();
            ban.setLiftedAt(now);
            ban.setLiftedBy(actor);
            ban.setLiftReason(reason);
            user.setAccountStatus(restoredStatus);
            user.incrementTokenVersion();
            userRepository.save(user);

            recordSuccess(ACTION_UNBAN, userId, reason,
                snapshot(restoredStatus, AccountStatus.BANNED, beforeTokenVersion,
                    SanctionType.BAN, ban.getExpiresAt()),
                snapshot(restoredStatus, user.getAccountStatus(), user.getTokenVersion(), null, null));
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_UNBAN, userId, reason, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    /** Suspends the account for one of the fixed durations agreed by the frontend contract. */
    @Transactional
    public void suspend(UUID userId, AdminUserSuspendRequest request) {
        String reason = requireReason(request);
        int durationHours = requireDuration(request);
        AdminUser actor = currentAdmin();

        try {
            User user = lockedUser(userId, ACTION_SUSPEND, reason);
            ensureNotAnonymised(user, userId, ACTION_SUSPEND, reason);

            if (user.getAccountStatus() == AccountStatus.BANNED) {
                throw failure(ACTION_SUSPEND, userId, reason,
                    AdminErrorCode.USER_ALREADY_BANNED,
                    "A banned user cannot be suspended. Lift the ban first.");
            }
            if (user.getAccountStatus() == AccountStatus.SUSPENDED) {
                throw failure(ACTION_SUSPEND, userId, reason, VALIDATION_ERROR,
                    "User is already suspended.");
            }

            Instant now = clock.instant();
            AccountStatus previousStatus = user.getAccountStatus();
            int beforeTokenVersion = user.getTokenVersion();
            Instant expiresAt = now.plus(Duration.ofHours(durationHours));
            UserSanction sanction = UserSanction.builder()
                .user(user)
                .adminUser(actor)
                .type(SanctionType.SUSPENSION)
                .reason(reason)
                .previousStatus(previousStatus)
                .startsAt(now)
                .expiresAt(expiresAt)
                .build();

            userSanctionRepository.save(sanction);
            user.setAccountStatus(AccountStatus.SUSPENDED);
            user.incrementTokenVersion();
            refreshTokenService.revoke(user);
            userRepository.save(user);

            recordSuccess(ACTION_SUSPEND, userId, reason,
                snapshot(previousStatus, previousStatus, beforeTokenVersion, null, null),
                snapshot(previousStatus, user.getAccountStatus(), user.getTokenVersion(),
                    SanctionType.SUSPENSION, expiresAt));
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_SUSPEND, userId, reason, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    /** Records a bilingual warning without changing account status or attempting delivery. */
    @Transactional
    public AdminUserWarningResponse warn(UUID userId, AdminUserWarningRequest request) {
        String reason = requireReason(request);
        String messageEn = requireMessage(request == null ? null : request.messageEn(), "messageEn");
        String messageKm = requireMessage(request == null ? null : request.messageKm(), "messageKm");
        AdminUser actor = currentAdmin();

        try {
            User user = lockedUser(userId, ACTION_WARNING, reason);
            ensureNotAnonymised(user, userId, ACTION_WARNING, reason);

            Instant now = clock.instant();
            UserSanction warning = UserSanction.builder()
                .user(user)
                .adminUser(actor)
                .type(SanctionType.WARNING)
                .severity(request.severity())
                .reason(reason)
                .messageEn(messageEn)
                .messageKm(messageKm)
                .previousStatus(user.getAccountStatus())
                .startsAt(now)
                .expiresAt(null)
                .build();
            userSanctionRepository.save(warning);

            adminAuditService.record(AdminAuditEvent.builder()
                .action(ACTION_WARNING)
                .targetType(TARGET_TYPE_USER)
                .targetId(userId)
                .reason(reason)
                .afterJson(adminAuditService.toJson(new WarningSnapshot(
                    warning.getSeverity(), WARNING_DELIVERY_STATUS)))
                .build());

            return new AdminUserWarningResponse(
                warning.getId(),
                userId,
                warning.getSeverity(),
                warning.getMessageEn(),
                warning.getMessageKm(),
                false,
                WARNING_DELIVERY_STATUS,
                warning.getStartsAt());
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_WARNING, userId, reason, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    /** Lifts one identified sanction without allowing a newer account action to be overwritten. */
    @Transactional
    public void lift(UUID sanctionId, AdminUserSanctionRequest request) {
        String reason = requireReason(request);
        AdminUser actor = currentAdmin();

        try {
            UserSanction reference = userSanctionRepository.findById(sanctionId)
                .orElseThrow(() -> sanctionNotFound(sanctionId, reason));
            User referenceUser = reference.getUser();
            if (referenceUser == null || referenceUser.getId() == null) {
                throw sanctionFailure(sanctionId, reason, INTERNAL_SERVER_ERROR,
                    "The sanction does not contain a valid target user.");
            }

            // Match the action services' lock order: target user first, then the sanction row.
            User user = lockedUser(referenceUser.getId(), ACTION_LIFT, reason);
            ensureNotAnonymised(user, referenceUser.getId(), ACTION_LIFT, reason);
            UserSanction sanction = userSanctionRepository.findByIdForUpdate(sanctionId)
                .orElseThrow(() -> sanctionNotFound(sanctionId, reason));

            if (sanction.getDeletedAt() != null || sanction.getLiftedAt() != null) {
                throw sanctionFailure(sanctionId, reason, VALIDATION_ERROR,
                    "Sanction is already lifted or unavailable.");
            }

            AccountStatus beforeStatus = user.getAccountStatus();
            int beforeTokenVersion = user.getTokenVersion();
            boolean userChanged = false;
            if (sanction.getType() == SanctionType.BAN) {
                if (user.getAccountStatus() != AccountStatus.BANNED) {
                    throw failure(ACTION_LIFT, referenceUser.getId(), reason,
                        AdminErrorCode.USER_NOT_BANNED, "User is not currently banned.");
                }
                AccountStatus restoredStatus = validRestoredStatus(sanction, sanctionId, reason);
                user.setAccountStatus(restoredStatus);
                user.incrementTokenVersion();
                userChanged = true;
            } else if (sanction.getType() == SanctionType.SUSPENSION
                && user.getAccountStatus() == AccountStatus.SUSPENDED) {
                AccountStatus restoredStatus = validRestoredStatus(sanction, sanctionId, reason);
                user.setAccountStatus(restoredStatus);
                user.incrementTokenVersion();
                refreshTokenService.revoke(user);
                userChanged = true;
            }

            Instant now = clock.instant();
            sanction.setLiftedAt(now);
            sanction.setLiftedBy(actor);
            sanction.setLiftReason(reason);
            userSanctionRepository.save(sanction);
            if (userChanged) {
                userRepository.save(user);
            }

            adminAuditService.record(AdminAuditEvent.builder()
                .action(ACTION_LIFT)
                .targetType(TARGET_TYPE_USER)
                .targetId(referenceUser.getId())
                .reason(reason)
                .beforeJson(adminAuditService.toJson(new LiftSnapshot(
                    sanction.getType(), beforeStatus, beforeTokenVersion, false)))
                .afterJson(adminAuditService.toJson(new LiftSnapshot(
                    sanction.getType(), user.getAccountStatus(), user.getTokenVersion(), true)))
                .build());
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            adminAuditService.recordFailure(
                ACTION_LIFT, TARGET_TYPE_USER, sanctionId, reason, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    private User lockedUser(UUID userId, String action, String reason) {
        return lockedUser(userId, action, reason, null);
    }

    private User lockedUser(UUID userId, String action, String reason, UUID batchId) {
        return userRepository.findByIdForUpdate(userId).orElseGet(() -> {
            recordFailure(action, userId, reason, "NOT_FOUND", batchId);
            throw new NotFoundException("User not found with id: " + userId);
        });
    }

    private void ensureNotAnonymised(
        User user, UUID userId, String action, String reason) {
        ensureNotAnonymised(user, userId, action, reason, null);
    }

    private void ensureNotAnonymised(
        User user, UUID userId, String action, String reason, UUID batchId) {
        if (user.getAccountStatus() == AccountStatus.DELETED) {
            throw failure(action, userId, reason,
                AdminErrorCode.USER_ANONYMISED,
                "This account can no longer be changed.", batchId);
        }
    }

    private void recordSuccess(
        String action,
        UUID userId,
        String reason,
        UserStateSnapshot before,
        UserStateSnapshot after) {
        recordSuccess(action, userId, reason, before, after, null);
    }

    private void recordSuccess(
        String action,
        UUID userId,
        String reason,
        UserStateSnapshot before,
        UserStateSnapshot after,
        UUID batchId) {
        adminAuditService.record(AdminAuditEvent.builder()
            .action(action)
            .targetType(TARGET_TYPE_USER)
            .targetId(userId)
            .reason(reason)
            .batchId(batchId)
            .beforeJson(adminAuditService.toJson(before))
            .afterJson(adminAuditService.toJson(after))
            .build());
    }

    private void recordFailure(String action, UUID userId, String reason, String errorCode) {
        recordFailure(action, userId, reason, errorCode, null);
    }

    private void recordFailure(
        String action, UUID userId, String reason, String errorCode, UUID batchId) {
        if (batchId == null) {
            adminAuditService.recordFailure(action, TARGET_TYPE_USER, userId, reason, errorCode);
        } else {
            adminAuditService.recordFailure(
                action, TARGET_TYPE_USER, userId, reason, errorCode, batchId);
        }
    }

    private ApiException sanctionNotFound(UUID sanctionId, String reason) {
        adminAuditService.recordFailure(
            ACTION_LIFT, "SANCTION", sanctionId, reason, "NOT_FOUND");
        return new NotFoundException("Sanction not found with id: " + sanctionId);
    }

    private ApiException sanctionFailure(
        UUID sanctionId,
        String reason,
        String errorCode,
        String message) {
        adminAuditService.recordFailure(
            ACTION_LIFT, "SANCTION", sanctionId, reason, errorCode);
        return new ApiException(message, errorCode);
    }

    private AccountStatus validRestoredStatus(
        UserSanction sanction,
        UUID sanctionId,
        String reason) {
        AccountStatus restoredStatus = sanction.getPreviousStatus();
        if (restoredStatus == null || restoredStatus == AccountStatus.BANNED
            || restoredStatus == AccountStatus.DELETED || restoredStatus == AccountStatus.SUSPENDED) {
            throw sanctionFailure(sanctionId, reason, INTERNAL_SERVER_ERROR,
                "The sanction does not contain a valid previous account status.");
        }
        return restoredStatus;
    }

    private ApiException failure(
        String action,
        UUID userId,
        String reason,
        String errorCode,
        String message) {
        return failure(action, userId, reason, errorCode, message, null);
    }

    private ApiException failure(
        String action,
        UUID userId,
        String reason,
        String errorCode,
        String message,
        UUID batchId) {
        recordFailure(action, userId, reason, errorCode, batchId);
        return new ApiException(message, errorCode);
    }

    private static String requireReason(AdminUserSanctionRequest request) {
        if (request == null || request.reason() == null || request.reason().trim().length() < 10) {
            throw new ApiException("reason: size must be between 10 and 2000", VALIDATION_ERROR);
        }
        return request.reason().trim();
    }

    private static String requireReason(AdminUserSuspendRequest request) {
        if (request == null || request.reason() == null || request.reason().trim().length() < 10) {
            throw new ApiException("reason: size must be between 10 and 2000", VALIDATION_ERROR);
        }
        return request.reason().trim();
    }

    private static String requireReason(AdminUserWarningRequest request) {
        if (request == null || request.reason() == null || request.reason().trim().length() < 10) {
            throw new ApiException("reason: size must be between 10 and 2000", VALIDATION_ERROR);
        }
        return request.reason().trim();
    }

    private static String requireMessage(String message, String field) {
        if (message == null || message.trim().isEmpty() || message.trim().length() > 2_000) {
            throw new ApiException(field + ": must contain between 1 and 2000 characters", VALIDATION_ERROR);
        }
        return message.trim();
    }

    private static int requireDuration(AdminUserSuspendRequest request) {
        if (request == null || request.durationHours() == null
            || !ALLOWED_SUSPENSION_HOURS.contains(request.durationHours())) {
            throw new ApiException("durationHours: must be one of 24, 168, or 720", VALIDATION_ERROR);
        }
        return request.durationHours();
    }

    private AdminUser currentAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new UnauthorizedException("Your session is no longer valid.");
        }
        return adminUserRepository.findActiveByUserEmail(authentication.getName())
            .orElseThrow(() -> new UnauthorizedException("Your session is no longer valid."));
    }

    private static UserStateSnapshot snapshot(
        AccountStatus previousStatus,
        AccountStatus accountStatus,
        int tokenVersion,
        SanctionType sanctionType,
        Instant expiresAt) {
        return new UserStateSnapshot(
            previousStatus, accountStatus, tokenVersion, sanctionType, expiresAt);
    }

    private record UserStateSnapshot(
        AccountStatus previousStatus,
        AccountStatus accountStatus,
        int tokenVersion,
        SanctionType sanctionType,
        Instant expiresAt) {
    }

    private record WarningSnapshot(SanctionSeverity severity, String deliveryStatus) {
    }

    private record LiftSnapshot(
        SanctionType sanctionType,
        AccountStatus accountStatus,
        int tokenVersion,
        boolean lifted) {
    }
}
