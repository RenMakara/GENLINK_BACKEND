package kh.edu.acleda.campuslink.modules.admin.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.MfaConfirmResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.MfaEnrolResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.Base32;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.RecoveryCodeService;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.TotpGenerator;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.auth.SecurityAuditService;
import kh.edu.acleda.campuslink.shared.crypto.FieldEncryptor;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import kh.edu.acleda.campuslink.shared.security.TokenType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TOTP enrolment: {@code /admin/auth/mfa/enrol} then {@code /admin/auth/mfa/confirm} (ADM-022). */
@Slf4j
@Service
public class AdminMfaEnrolmentService {

    /** Shown by the authenticator app above the code. */
    private static final String ISSUER = "GenLink KH Admin";

    private static final String EVENT_ENROL_STARTED = "ADMIN_MFA_ENROL_STARTED";
    private static final String EVENT_ENROL_CONFIRMED = "ADMIN_MFA_ENROL_CONFIRMED";
    private static final String EVENT_ENROL_FAILED = "ADMIN_MFA_ENROL_FAILED";

    private final AdminUserRepository adminUserRepository;
    private final JwtService jwtService;
    private final FieldEncryptor fieldEncryptor;
    private final TotpGenerator totpGenerator;
    private final RecoveryCodeService recoveryCodeService;
    private final SecurityAuditService securityAuditService;

    public AdminMfaEnrolmentService(AdminUserRepository adminUserRepository,
                                    JwtService jwtService,
                                    FieldEncryptor fieldEncryptor,
                                    TotpGenerator totpGenerator,
                                    RecoveryCodeService recoveryCodeService,
                                    SecurityAuditService securityAuditService) {
        this.adminUserRepository = adminUserRepository;
        this.jwtService = jwtService;
        this.fieldEncryptor = fieldEncryptor;
        this.totpGenerator = totpGenerator;
        this.recoveryCodeService = recoveryCodeService;
        this.securityAuditService = securityAuditService;
    }

    /** Issues a fresh secret and the URI the portal renders as a QR code. */
    @Transactional
    public MfaEnrolResponse enrol(String challengeToken) {
        AdminUser admin = resolveFromChallenge(challengeToken);

        if (admin.isEnrolled()) {
            securityAuditService.logFailure(EVENT_ENROL_FAILED, emailOf(admin), "ALREADY_ENROLLED");
            throw new ApiException(
                "Two-factor authentication is already set up for this account.",
                AdminErrorCode.ADMIN_MFA_ALREADY_ENROLLED);
        }

        byte[] secret = totpGenerator.generateSecret();
        String base32Secret = Base32.encode(secret);

        // Encrypt the secret before assigning it to the entity.
        admin.setTotpSecret(fieldEncryptor.encrypt(base32Secret));
        admin.setTotpEnabled(false);
        adminUserRepository.save(admin);

        securityAuditService.logSuccess(EVENT_ENROL_STARTED, admin.getUser(), emailOf(admin));
        // No secret, no URI: the URI contains the secret.
        log.info("TOTP enrolment started for admin {}", emailOf(admin));

        return new MfaEnrolResponse(base32Secret, otpauthUri(emailOf(admin), base32Secret));
    }

    /** Verifies the first code, switches TOTP on, and returns the recovery codes. */
    @Transactional
    public MfaConfirmResponse confirm(String challengeToken, String code) {
        AdminUser admin = resolveFromChallenge(challengeToken);

        if (admin.isEnrolled()) {
            securityAuditService.logFailure(EVENT_ENROL_FAILED, emailOf(admin), "ALREADY_ENROLLED");
            throw new ApiException(
                "Two-factor authentication is already set up for this account.",
                AdminErrorCode.ADMIN_MFA_ALREADY_ENROLLED);
        }
        if (admin.getTotpSecret() == null) {
            securityAuditService.logFailure(EVENT_ENROL_FAILED, emailOf(admin), "NO_PENDING_ENROLMENT");
            throw new ApiException(
                "Start enrolment before confirming it.", AdminErrorCode.ADMIN_MFA_INVALID);
        }

        byte[] secret = Base32.decode(fieldEncryptor.decrypt(admin.getTotpSecret()));
        if (!totpGenerator.verify(secret, code, Instant.now())) {
            securityAuditService.logFailure(EVENT_ENROL_FAILED, emailOf(admin), "BAD_CODE");
            throw new UnauthorizedException("That code is not valid. Check your authenticator and try again.");
        }

        RecoveryCodeService.GeneratedCodes codes = recoveryCodeService.generate();
        admin.setRecoveryCodes(codes.json());
        admin.setTotpEnabled(true);
        admin.setTotpConfirmedAt(Instant.now());
        adminUserRepository.save(admin);

        securityAuditService.logSuccess(EVENT_ENROL_CONFIRMED, admin.getUser(), emailOf(admin));
        log.info("TOTP enrolment confirmed for admin {}", emailOf(admin));

        return new MfaConfirmResponse(codes.plaintext());
    }

    /** Validates the challenge token and loads the admin it names. */
    private AdminUser resolveFromChallenge(String challengeToken) {
        if (challengeToken == null || challengeToken.isBlank() || !jwtService.validateToken(challengeToken)) {
            throw new UnauthorizedException("Your sign-in session has expired. Please sign in again.");
        }
        if (jwtService.getTokenType(challengeToken) != TokenType.ADMIN_MFA_CHALLENGE) {
            log.warn("Non-challenge token presented to an MFA enrolment route");
            throw new UnauthorizedException("Your sign-in session has expired. Please sign in again.");
        }
        return adminUserRepository.findActiveByUserEmail(jwtService.getUsername(challengeToken))
            .orElseThrow(() -> new UnauthorizedException(
                "Your sign-in session has expired. Please sign in again."));
    }

    /** Builds the TOTP enrollment URI. */
    private static String otpauthUri(String account, String base32Secret) {
        return "otpauth://totp/" + encode(ISSUER) + ":" + encodeAccount(account)
            + "?secret=" + base32Secret
            + "&issuer=" + encode(ISSUER)
            + "&algorithm=SHA1"
            + "&digits=" + TotpGenerator.DIGITS
            + "&period=" + TotpGenerator.STEP_SECONDS;
    }

    /** URL-encodes, then repairs {@code +}: the Key Uri Format expects {@code %20} for a space. */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** As {@link #encode}, but leaves {@code @} alone. */
    private static String encodeAccount(String account) {
        return encode(account).replace("%40", "@");
    }

    private static String emailOf(AdminUser admin) {
        return admin.getUser().getEmail();
    }
}
