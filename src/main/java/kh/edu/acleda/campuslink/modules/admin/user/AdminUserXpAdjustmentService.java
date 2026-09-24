package kh.edu.acleda.campuslink.modules.admin.user;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.rbac.Permission;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserXpAdjustmentRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserXpAdjustmentResponse;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.ForbiddenException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Audited, permission-aware XP adjustments for the Admin user detail page (ADM-082). */
@Service
public class AdminUserXpAdjustmentService {

    private static final String SOURCE_TYPE = "ADMIN_ADJUSTMENT";
    private static final String TARGET_TYPE_USER = "USER";
    private static final String ACTION_GRANT = "USER_XP_GRANT";
    private static final String ACTION_REVOKE = "USER_XP_REVOKE";
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    private final UserRepository userRepository;
    private final RewardService rewardService;
    private final AdminAuditService adminAuditService;

    public AdminUserXpAdjustmentService(
        UserRepository userRepository,
        RewardService rewardService,
        AdminAuditService adminAuditService) {
        this.userRepository = userRepository;
        this.rewardService = rewardService;
        this.adminAuditService = adminAuditService;
    }

    /** Applies a signed delta through the shared reward pipeline and returns the resulting state. */
    @Transactional
    public AdminUserXpAdjustmentResponse adjust(
        UUID userId,
        AdminUserXpAdjustmentRequest request) {
        int requestedAmount = requireAmount(request);
        String reason = requireReason(request);
        boolean grant = requestedAmount > 0;
        String action = grant ? ACTION_GRANT : ACTION_REVOKE;
        requirePermission(grant ? Permission.XP_GRANT : Permission.XP_REVOKE);

        try {
            User user = lockedUser(userId, action, reason);
            UserXpSnapshot before = snapshot(user);
            int appliedAmount = rewardService.award(user, SOURCE_TYPE, null, requestedAmount);
            UserXpSnapshot after = snapshot(user);

            adminAuditService.record(AdminAuditEvent.builder()
                .action(action)
                .targetType(TARGET_TYPE_USER)
                .targetId(userId)
                .reason(reason)
                .beforeJson(adminAuditService.toJson(before))
                .afterJson(adminAuditService.toJson(after))
                .build());

            return new AdminUserXpAdjustmentResponse(
                userId, requestedAmount, appliedAmount, user.getTotalXp(), user.getLevel());
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            adminAuditService.recordFailure(action, TARGET_TYPE_USER, userId, reason,
                INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    private User lockedUser(UUID userId, String action, String reason) {
        return userRepository.findByIdForUpdate(userId).orElseGet(() -> {
            adminAuditService.recordFailure(action, TARGET_TYPE_USER, userId, reason, NOT_FOUND);
            throw new NotFoundException("User not found with id: " + userId);
        });
    }

    private static int requireAmount(AdminUserXpAdjustmentRequest request) {
        if (request == null || request.amount() == null || request.amount() == 0) {
            throw new ApiException("amount must be a non-zero signed integer", VALIDATION_ERROR);
        }
        return request.amount();
    }

    private static String requireReason(AdminUserXpAdjustmentRequest request) {
        if (request == null || request.reason() == null || request.reason().trim().length() < 10) {
            throw new ApiException("reason: size must be between 10 and 2000", VALIDATION_ERROR);
        }
        return request.reason().trim();
    }

    private static void requirePermission(Permission permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean permitted = authentication != null && authentication.getAuthorities().stream()
            .anyMatch(granted -> granted.getAuthority().equals(permission.asAuthority().getAuthority()));
        if (!permitted) {
            throw new ForbiddenException("You do not have permission to adjust XP in this direction.",
                "FORBIDDEN");
        }
    }

    private static UserXpSnapshot snapshot(User user) {
        return new UserXpSnapshot(user.getTotalXp(), user.getLevel());
    }

    private record UserXpSnapshot(int totalXp, int level) {
    }
}
