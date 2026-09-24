package kh.edu.acleda.campuslink.modules.admin.user;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserLifecycleRequest;
import kh.edu.acleda.campuslink.modules.auth.RefreshTokenService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional soft-delete and restore actions for the Admin portal (ADM-081). */
@Service
public class AdminUserLifecycleService {

    private static final String TARGET_TYPE_USER = "USER";
    private static final String ACTION_DELETE = "USER_DELETE";
    private static final String ACTION_RESTORE = "USER_RESTORE";
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AdminAuditService adminAuditService;
    private final Clock clock;

    @Autowired
    public AdminUserLifecycleService(
        UserRepository userRepository,
        RefreshTokenService refreshTokenService,
        AdminAuditService adminAuditService) {
        this(userRepository, refreshTokenService, adminAuditService, Clock.systemUTC());
    }

    AdminUserLifecycleService(
        UserRepository userRepository,
        RefreshTokenService refreshTokenService,
        AdminAuditService adminAuditService,
        Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.adminAuditService = adminAuditService;
        this.clock = clock;
    }

    /** Soft-deletes the row, preserving the lifecycle state needed for restoration. */
    @Transactional
    public void delete(UUID userId, AdminUserLifecycleRequest request) {
        String reason = requireReason(request);
        try {
            User user = lockedUser(userId, ACTION_DELETE, reason);

            // A repeated click must not replace the original deletion timestamp or captured
            // state. A legacy/anonymised row is already beyond the destructive transition too.
            if (isDeleted(user)) {
                recordSuccess(ACTION_DELETE, userId, reason, snapshot(user), snapshot(user));
                return;
            }

            AccountStatus previousStatus = user.getAccountStatus();
            if (previousStatus == null || previousStatus == AccountStatus.DELETED) {
                throw failure(ACTION_DELETE, userId, reason, INTERNAL_SERVER_ERROR,
                    "The account does not contain a valid status to restore.");
            }

            Instant now = clock.instant();
            int beforeTokenVersion = user.getTokenVersion();
            user.setStatusBeforeDeletion(previousStatus);
            user.setAccountStatus(AccountStatus.DELETED);
            user.setDeletedAt(now);
            user.incrementTokenVersion();
            userRepository.save(user);
            refreshTokenService.revoke(user);

            recordSuccess(ACTION_DELETE, userId, reason,
                snapshot(previousStatus, AccountStatus.DELETED, now, previousStatus,
                    beforeTokenVersion),
                snapshot(previousStatus, user.getAccountStatus(), user.getDeletedAt(),
                    user.getStatusBeforeDeletion(), user.getTokenVersion()));
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_DELETE, userId, reason, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    /** Restores a reversible soft-deleted row to exactly the status captured at deletion. */
    @Transactional
    public void restore(UUID userId, AdminUserLifecycleRequest request) {
        String reason = requireReason(request);
        try {
            User user = lockedUser(userId, ACTION_RESTORE, reason);
            if (!isDeleted(user)) {
                throw failure(ACTION_RESTORE, userId, reason, VALIDATION_ERROR,
                    "User is not currently deleted.");
            }

            AccountStatus previousStatus = user.getStatusBeforeDeletion();
            if (previousStatus == null || previousStatus == AccountStatus.DELETED) {
                throw failure(ACTION_RESTORE, userId, reason,
                    AdminErrorCode.USER_ANONYMISED,
                    "This account has been anonymised and cannot be restored.");
            }

            AccountSnapshot before = snapshot(user);
            user.setAccountStatus(previousStatus);
            user.setDeletedAt(null);
            user.setStatusBeforeDeletion(null);
            user.incrementTokenVersion();
            userRepository.save(user);

            recordSuccess(ACTION_RESTORE, userId, reason, before, snapshot(user));
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_RESTORE, userId, reason, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    private User lockedUser(UUID userId, String action, String reason) {
        return userRepository.findByIdForUpdate(userId).orElseGet(() -> {
            recordFailure(action, userId, reason, NOT_FOUND);
            throw new NotFoundException("User not found with id: " + userId);
        });
    }

    private static boolean isDeleted(User user) {
        return user.getAccountStatus() == AccountStatus.DELETED || user.getDeletedAt() != null;
    }

    private void recordSuccess(
        String action,
        UUID userId,
        String reason,
        Object before,
        Object after) {
        adminAuditService.record(AdminAuditEvent.builder()
            .action(action)
            .targetType(TARGET_TYPE_USER)
            .targetId(userId)
            .reason(reason)
            .beforeJson(adminAuditService.toJson(before))
            .afterJson(adminAuditService.toJson(after))
            .build());
    }

    private void recordFailure(String action, UUID userId, String reason, String errorCode) {
        adminAuditService.recordFailure(action, TARGET_TYPE_USER, userId, reason, errorCode);
    }

    private ApiException failure(
        String action,
        UUID userId,
        String reason,
        String errorCode,
        String message) {
        recordFailure(action, userId, reason, errorCode);
        return new ApiException(message, errorCode);
    }

    private static String requireReason(AdminUserLifecycleRequest request) {
        if (request == null || request.reason() == null || request.reason().trim().length() < 10) {
            throw new ApiException("reason: size must be between 10 and 2000", VALIDATION_ERROR);
        }
        return request.reason().trim();
    }

    private static AccountSnapshot snapshot(User user) {
        return snapshot(
            user.getStatusBeforeDeletion(),
            user.getAccountStatus(),
            user.getDeletedAt(),
            user.getStatusBeforeDeletion(),
            user.getTokenVersion());
    }

    private static AccountSnapshot snapshot(
        AccountStatus previousStatus,
        AccountStatus accountStatus,
        Instant deletedAt,
        AccountStatus statusBeforeDeletion,
        int tokenVersion) {
        return new AccountSnapshot(
            previousStatus, accountStatus, deletedAt, statusBeforeDeletion, tokenVersion);
    }

    private record AccountSnapshot(
        AccountStatus previousStatus,
        AccountStatus accountStatus,
        Instant deletedAt,
        AccountStatus statusBeforeDeletion,
        int tokenVersion) {
    }
}
