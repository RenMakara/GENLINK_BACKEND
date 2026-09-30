package kh.edu.acleda.campuslink.modules.admin.auth;

import java.time.Instant;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.request.SudoRequest;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.SudoResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.Base32;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.TotpGenerator;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.auth.SecurityAuditService;
import kh.edu.acleda.campuslink.shared.crypto.FieldEncryptor;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Step-up re-authentication (SSOT §10.7, ADM-025). */
@Slf4j
@Service
public class AdminSudoService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String EVENT_OPENED = "ADMIN_SUDO_OPENED";
    private static final String EVENT_FAILED = "ADMIN_SUDO_FAILED";

    private final AuthenticationManager authenticationManager;
    private final AdminUserRepository adminUserRepository;
    private final AdminSudoRegistry sudoRegistry;
    private final TotpGenerator totpGenerator;
    private final FieldEncryptor fieldEncryptor;
    private final SecurityAuditService securityAuditService;
    private final JwtService jwtService;

    public AdminSudoService(AuthenticationManager authenticationManager,
                            AdminUserRepository adminUserRepository,
                            AdminSudoRegistry sudoRegistry,
                            TotpGenerator totpGenerator,
                            FieldEncryptor fieldEncryptor,
                            SecurityAuditService securityAuditService,
                            JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.adminUserRepository = adminUserRepository;
        this.sudoRegistry = sudoRegistry;
        this.totpGenerator = totpGenerator;
        this.fieldEncryptor = fieldEncryptor;
        this.securityAuditService = securityAuditService;
        this.jwtService = jwtService;
    }

    /** Re-authenticates with a password or a fresh TOTP code and opens the window. */
    @Transactional(readOnly = true)
    public SudoResponse open(SudoRequest request) {
        String sessionId = currentSessionId();
        if (sessionId == null) {
            // Only reachable with a token predating session ids; treat as unusable rather than
            // opening a window nothing can be scoped to.
            throw new UnauthorizedException("Your session is no longer valid. Please sign in again.");
        }

        String email = currentEmail();
        AdminUser admin = adminUserRepository.findActiveByUserEmail(email)
            .orElseThrow(() -> new UnauthorizedException("Your session is no longer valid."));

        boolean accepted = request.code() != null && !request.code().isBlank()
            ? verifyTotp(admin, request.code())
            : verifyPassword(email, request.password());

        if (!accepted) {
            securityAuditService.logFailure(EVENT_FAILED, admin.getUser(), email, "BAD_CREDENTIAL");
            // Warn, not debug: a failed step-up on an already-authenticated session is either a
            // typo or somebody at a machine that is not theirs.
            log.warn("Sudo re-authentication failed for admin {}", email);
            throw new UnauthorizedException("That did not match. Please try again.");
        }

        Instant expiry = sudoRegistry.open(sessionId);
        securityAuditService.logSuccess(EVENT_OPENED, admin.getUser(), email);
        log.info("Sudo window opened for admin {}", email);

        return new SudoResponse(AdminSudoRegistry.WINDOW.toSeconds(), expiry);
    }

    /** Whether the caller currently holds an open window. */
    public boolean isOpen() {
        return sudoRegistry.isOpen(currentSessionId());
    }

    public long secondsRemaining() {
        return sudoRegistry.secondsRemaining(currentSessionId());
    }

    /** Closes the caller's window - called on logout so nothing armed outlives the session. */
    public void close(String sessionId) {
        sudoRegistry.close(sessionId);
    }

    private boolean verifyTotp(AdminUser admin, String code) {
        if (admin.getTotpSecret() == null || !admin.isEnrolled()) {
            return false;
        }
        byte[] secret = Base32.decode(fieldEncryptor.decrypt(admin.getTotpSecret()));
        return totpGenerator.verify(secret, code.trim(), Instant.now());
    }

    /** Reuses the configured {@link AuthenticationManager}. */
    private boolean verifyPassword(String email, String password) {
        if (password == null || password.isBlank()) {
            return false;
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
            return true;
        } catch (AuthenticationException | UnauthorizedException ex) {
            return false;
        }
    }

    /** The session this request belongs to, read from the access token's {@code jti}. */
    String currentSessionId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        String header = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length());
        return jwtService.validateToken(token) ? jwtService.getTokenId(token) : null;
    }

    private static String currentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("Your session is no longer valid.");
        }
        return authentication.getName();
    }
}
