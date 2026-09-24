package kh.edu.acleda.campuslink.modules.admin.user;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Audited, owner-scoped single-session revocation for the Admin portal (ADM-085). */
@Service
public class AdminUserSessionRevocationService {

    private static final String ACTION_REVOKE = "USER_SESSION_REVOKE";
    private static final String TARGET_TYPE_USER = "USER";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminAuditService adminAuditService;
    private final Clock clock;

    @Autowired
    public AdminUserSessionRevocationService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        AdminAuditService adminAuditService) {
        this(userRepository, refreshTokenRepository, adminAuditService, Clock.systemUTC());
    }

    AdminUserSessionRevocationService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        AdminAuditService adminAuditService,
        Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.adminAuditService = adminAuditService;
        this.clock = clock;
    }

    /**
     * Revokes only the selected refresh session. The access token already issued to that
     * device remains valid until its normal expiry because token_version is per-user.
     */
    @Transactional
    public void revoke(UUID userId, UUID sessionId) {
        try {
            User user = requireUser(userId);
            RefreshToken session = requireSession(userId, sessionId, user);

            // Keep DELETE idempotent: a session that is already gone from the active list is
            // still a successful no-op, while an unowned session remains a 404 above.
            if (session.getRevokedAt() == null) {
                Instant revokedAt = clock.instant();
                session.setRevokedAt(revokedAt);
                refreshTokenRepository.save(session);

                adminAuditService.record(AdminAuditEvent.builder()
                    .action(ACTION_REVOKE)
                    .targetType(TARGET_TYPE_USER)
                    .targetId(userId)
                    .afterJson(adminAuditService.toJson(
                        new SessionRevocationSnapshot(sessionId, revokedAt)))
                    .build());
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            adminAuditService.recordFailure(
                ACTION_REVOKE, TARGET_TYPE_USER, userId, null, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseGet(() -> {
            adminAuditService.recordFailure(ACTION_REVOKE, TARGET_TYPE_USER, userId, null, NOT_FOUND);
            throw new NotFoundException("User not found with id: " + userId);
        });
    }

    private RefreshToken requireSession(UUID userId, UUID sessionId, User user) {
        return refreshTokenRepository.findByIdAndUser(sessionId, user).orElseGet(() -> {
            adminAuditService.recordFailure(ACTION_REVOKE, TARGET_TYPE_USER, userId, null, NOT_FOUND);
            throw new NotFoundException("Session not found");
        });
    }

    private record SessionRevocationSnapshot(UUID sessionId, Instant revokedAt) {
    }
}
