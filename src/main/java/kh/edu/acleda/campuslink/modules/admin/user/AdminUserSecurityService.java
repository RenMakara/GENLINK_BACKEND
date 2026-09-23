package kh.edu.acleda.campuslink.modules.admin.user;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserLoginHistoryResponse;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserSessionResponse;
import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.auth.entity.SecurityAuditLog;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.auth.repository.SecurityAuditLogRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, owner-scoped security history for the admin user detail page. */
@Service
public class AdminUserSecurityService {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(25, 50, 100);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityAuditLogRepository securityAuditLogRepository;
    private final Clock clock;

    @Autowired
    public AdminUserSecurityService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        SecurityAuditLogRepository securityAuditLogRepository) {
        this(userRepository, refreshTokenRepository, securityAuditLogRepository, Clock.systemUTC());
    }

    AdminUserSecurityService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        SecurityAuditLogRepository securityAuditLogRepository,
        Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.securityAuditLogRepository = securityAuditLogRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserSessionResponse> sessions(UUID userId, int page, int size) {
        requireUser(userId);
        Pageable pageable = pageRequest(page, size);
        Page<RefreshToken> sessions = refreshTokenRepository.findActiveByUserId(
            userId, clock.instant(), pageable);
        return PageResponse.from(sessions, AdminUserSecurityService::toSessionResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserLoginHistoryResponse> loginHistory(UUID userId, int page, int size) {
        requireUser(userId);
        Pageable pageable = pageRequest(page, size);
        Page<SecurityAuditLog> history = securityAuditLogRepository
            .findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(history, AdminUserSecurityService::toLoginHistoryResponse);
    }

    private void requireUser(UUID userId) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new NotFoundException("User not found with id: " + userId);
        }
    }

    private static Pageable pageRequest(int page, int size) {
        if (page < 0) {
            throw invalid("page must be zero or greater");
        }
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw invalid("size must be one of 25, 50, or 100");
        }
        return PageRequest.of(page, size);
    }

    private static ApiException invalid(String message) {
        return new ApiException(message, "VALIDATION_ERROR");
    }

    private static AdminUserSessionResponse toSessionResponse(RefreshToken session) {
        return new AdminUserSessionResponse(
            session.getId(),
            session.getDeviceInfo(),
            session.getIpAddress(),
            session.getUserAgent(),
            session.getCreatedAt(),
            session.getLastUsedAt(),
            session.getExpiresAt()
        );
    }

    private static AdminUserLoginHistoryResponse toLoginHistoryResponse(SecurityAuditLog auditLog) {
        boolean success = Boolean.TRUE.equals(auditLog.getSuccess())
            || (auditLog.getSuccess() == null && "SUCCESS".equalsIgnoreCase(auditLog.getStatus()));
        return new AdminUserLoginHistoryResponse(
            auditLog.getId(),
            auditLog.getEventType(),
            success,
            auditLog.getFailureReason(),
            auditLog.getIpAddress(),
            auditLog.getUserAgent(),
            auditLog.getCreatedAt()
        );
    }
}
