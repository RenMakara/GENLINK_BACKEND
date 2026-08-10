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

/**
 * Reissues the ten recovery codes (ADM-026).
 *
 * <h2>Why this endpoint exists</h2>
 *
 * Recovery codes are single-use and shown exactly once. Without a way to regenerate them, an
 * admin who spends their set has no path back except an MFA reset by someone holding
 * {@code rbac:manage} - and for a lone {@code SUPER_ADMIN} that means database access. It was
 * added in review of SSOT §8.1 for precisely that reason.
 *
 * <h2>Separate from {@link AdminMfaEnrolmentService} on purpose</h2>
 *
 * That service is authorised by the five-minute <em>challenge</em> token, because the admin
 * using it has no session yet. This one is the opposite: it needs a full admin session
 * <em>and</em> an open sudo window. Putting both in one class would mean one file with two
 * contradictory authorisation stories, which is how the wrong one eventually gets applied.
 */
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

    /**
     * Replaces the whole set with ten fresh codes.
     *
     * <p>The stored JSON is overwritten rather than appended to, so every previous code - spent
     * or not - stops working the moment this returns. Merging the two sets would leave codes in
     * circulation that the admin regenerated specifically because they no longer trusted them.
     *
     * <p>The sudo window is enforced by {@link RequiresSudo} on the controller method, so a
     * stolen session alone cannot mint a fresh set of permanent bypass codes.
     */
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
        // Worth an info line: regeneration invalidates every existing code, so if an admin ever
        // reports "my recovery codes stopped working", this is the first thing to look for.
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
