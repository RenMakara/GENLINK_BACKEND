package kh.edu.acleda.campuslink.modules.admin.auth;

import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.RecoveryCodesResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.RecoveryCodeService;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.auth.SecurityAuditService;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reissues the ten recovery codes (ADM-026). */
@Slf4j
@Service
public class AdminRecoveryCodeService {

    private static final String EVENT_REGENERATED = "ADMIN_RECOVERY_CODES_REGENERATED";
    private static final String EVENT_FAILED = "ADMIN_RECOVERY_CODES_FAILED";

    private final AdminUserRepository adminUserRepository;
    private final RecoveryCodeService recoveryCodeService;
    private final SecurityAuditService securityAuditService;

    public AdminRecoveryCodeService(AdminUserRepository adminUserRepository,
                                    RecoveryCodeService recoveryCodeService,
                                    SecurityAuditService securityAuditService) {
        this.adminUserRepository = adminUserRepository;
        this.recoveryCodeService = recoveryCodeService;
        this.securityAuditService = securityAuditService;
    }

    /** Replaces the whole set with ten fresh codes. */
    @Transactional
    public RecoveryCodesResponse regenerate() {
        String email = currentEmail();
        AdminUser admin = adminUserRepository.findActiveByUserEmail(email)
            .orElseThrow(() -> new UnauthorizedException("Your session is no longer valid."));

        if (!admin.isEnrolled()) {
            securityAuditService.logFailure(EVENT_FAILED, admin.getUser(), email, "NOT_ENROLLED");
            throw new ApiException(
                "Set up two-factor authentication before generating recovery codes.",
                AdminErrorCode.ADMIN_MFA_NOT_ENROLLED);
        }

        RecoveryCodeService.GeneratedCodes codes = recoveryCodeService.generate();
        admin.setRecoveryCodes(codes.json());
        adminUserRepository.save(admin);

        securityAuditService.logSuccess(EVENT_REGENERATED, admin.getUser(), email);
        // Worth an info line: regeneration invalidates every existing code.
        log.info("Recovery codes regenerated for admin {} - the previous set is now invalid", email);

        return new RecoveryCodesResponse(codes.plaintext());
    }

    private static String currentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("Your session is no longer valid.");
        }
        return authentication.getName();
    }
}
