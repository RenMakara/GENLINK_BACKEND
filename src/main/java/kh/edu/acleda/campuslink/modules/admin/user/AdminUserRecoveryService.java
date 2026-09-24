package kh.edu.acleda.campuslink.modules.admin.user;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.auth.AuthService;
import kh.edu.acleda.campuslink.modules.auth.RefreshTokenService;
import kh.edu.acleda.campuslink.modules.auth.dto.ForgotPasswordRequest;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional account-recovery actions for the Admin portal (ADM-080). */
@Service
@RequiredArgsConstructor
public class AdminUserRecoveryService {

    private static final String TARGET_TYPE_USER = "USER";
    private static final String ACTION_VERIFY_EMAIL = "USER_VERIFY_EMAIL";
    private static final String ACTION_RESET_PASSWORD = "USER_RESET_PASSWORD";
    private static final String ACTION_FORCE_LOGOUT = "USER_FORCE_LOGOUT";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    private final UserRepository userRepository;
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final AdminAuditService adminAuditService;

    /** Marks the address verified; an unverified PENDING account becomes ACTIVE. */
    @Transactional
    public void verifyEmail(UUID userId) {
        try {
            User user = lockedUser(userId, ACTION_VERIFY_EMAIL);
            ensureNotAnonymised(user, userId, ACTION_VERIFY_EMAIL);

            VerificationSnapshot before = snapshot(user);
            if (!user.isEmailVerified()) {
                user.setEmailVerified(true);
                if (user.getAccountStatus() == AccountStatus.PENDING) {
                    user.setAccountStatus(AccountStatus.ACTIVE);
                }
                userRepository.save(user);
            }

            recordSuccess(ACTION_VERIFY_EMAIL, userId, before, snapshot(user));
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_VERIFY_EMAIL, userId, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    /** Starts the existing user-facing OTP reset flow; no password crosses the admin API. */
    @Transactional
    public void resetPassword(UUID userId) {
        try {
            User user = lockedUser(userId, ACTION_RESET_PASSWORD);
            ensureNotAnonymised(user, userId, ACTION_RESET_PASSWORD);

            authService.forgotPassword(new ForgotPasswordRequest(user.getEmail()));
            recordSuccess(ACTION_RESET_PASSWORD, userId, null, null);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_RESET_PASSWORD, userId, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    /** Revokes every refresh session and invalidates already-issued access tokens immediately. */
    @Transactional
    public void forceLogout(UUID userId) {
        try {
            User user = lockedUser(userId, ACTION_FORCE_LOGOUT);
            ensureNotAnonymised(user, userId, ACTION_FORCE_LOGOUT);

            TokenVersionSnapshot before = new TokenVersionSnapshot(user.getTokenVersion());
            refreshTokenService.revoke(user);
            user.incrementTokenVersion();
            userRepository.save(user);

            TokenVersionSnapshot after = new TokenVersionSnapshot(user.getTokenVersion());
            adminAuditService.record(AdminAuditEvent.builder()
                .action(ACTION_FORCE_LOGOUT)
                .targetType(TARGET_TYPE_USER)
                .targetId(userId)
                .beforeJson(adminAuditService.toJson(before))
                .afterJson(adminAuditService.toJson(after))
                .build());
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_FORCE_LOGOUT, userId, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    private User lockedUser(UUID userId, String action) {
        return userRepository.findByIdForUpdate(userId).orElseGet(() -> {
            recordFailure(action, userId, NOT_FOUND);
            throw new NotFoundException("User not found with id: " + userId);
        });
    }

    private void ensureNotAnonymised(User user, UUID userId, String action) {
        if (user.getAccountStatus() == AccountStatus.DELETED || user.getDeletedAt() != null) {
            recordFailure(action, userId, AdminErrorCode.USER_ANONYMISED);
            throw new ApiException(
                "This account can no longer be changed.", AdminErrorCode.USER_ANONYMISED);
        }
    }

    private void recordSuccess(
        String action,
        UUID userId,
        VerificationSnapshot before,
        VerificationSnapshot after) {
        adminAuditService.record(AdminAuditEvent.builder()
            .action(action)
            .targetType(TARGET_TYPE_USER)
            .targetId(userId)
            .beforeJson(adminAuditService.toJson(before))
            .afterJson(adminAuditService.toJson(after))
            .build());
    }

    private void recordFailure(String action, UUID userId, String errorCode) {
        adminAuditService.recordFailure(action, TARGET_TYPE_USER, userId, null, errorCode);
    }

    private static VerificationSnapshot snapshot(User user) {
        return new VerificationSnapshot(user.isEmailVerified(), user.getAccountStatus());
    }

    private record VerificationSnapshot(boolean emailVerified, AccountStatus accountStatus) {
    }

    private record TokenVersionSnapshot(int tokenVersion) {
    }
}
