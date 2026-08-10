package kh.edu.acleda.campuslink.modules.admin.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.request.AdminLoginRequest;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.request.AdminRefreshRequest;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.request.MfaConfirmRequest;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.request.MfaVerifyRequest;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.request.SudoRequest;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.AdminMeResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.AdminMfaChallengeResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.AdminTokenResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.MfaConfirmResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.MfaEnrolResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.RecoveryCodesResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.SudoResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/admin/auth} - sign-in for the admin portal.
 *
 * <p>Public by necessity: an admin has no token yet when they arrive. It is reachable only
 * through the restricted channel described in SSOT §10.9.1, and it is the most attacked
 * endpoint in the application, which is why {@link AdminAuthServiceImpl} treats every rejection
 * as indistinguishable.
 */
@Tag(name = "Admin - Auth", description = "Sign-in, MFA and session lifecycle for the admin portal (SSOT §8.1)")
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AdminAuthService adminAuthService;
    private final AdminMfaEnrolmentService enrolmentService;
    private final AdminSessionService sessionService;
    private final AdminSudoService sudoService;
    private final AdminRecoveryCodeService recoveryCodeService;
    private final AdminMfaVerificationService verificationService;

    /**
     * Password step. A correct password buys a TOTP challenge, not a session.
     *
     * <p>Answers <strong>401</strong> on success, which reads oddly and is deliberate: the
     * caller is not authenticated yet and holds nothing that authenticates them. The response
     * carries {@code ADMIN_MFA_REQUIRED} so the portal can tell "finish signing in" apart from
     * "those credentials are wrong", both of which are 401s (SSOT §8 C-2).
     */
    @Operation(summary = "Admin login - password step",
        description = "Verifies credentials and issues a 5-minute MFA challenge token. "
            + "Never returns an access token: 401 with ADMIN_MFA_REQUIRED is the success case.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminMfaChallengeResponse>> login(
        @Valid @RequestBody AdminLoginRequest request) {

        AdminMfaChallengeResponse challenge = adminAuthService.login(request);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.failure(
                "Multi-factor authentication required",
                AdminErrorCode.ADMIN_MFA_REQUIRED,
                challenge));
    }

    /**
     * Begins TOTP enrolment. Authorised by the challenge token from {@code /login}, presented
     * as a bearer token - see {@link AdminMfaEnrolmentService} for why it is not an admin
     * session.
     */
    @Operation(summary = "Begin TOTP enrolment",
        description = "Returns a fresh Base32 secret and an otpauth:// URI for QR rendering. "
            + "Send the challenge token from /login as the bearer token. Nothing is switched "
            + "on until /mfa/confirm succeeds.")
    @PostMapping("/mfa/enrol")
    public ApiResponse<MfaEnrolResponse> enrol(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        return ApiResponse.success("Scan this with your authenticator app",
            enrolmentService.enrol(bearerToken(authorization)));
    }

    /**
     * Confirms enrolment and returns the recovery codes - the only time they are ever shown.
     */
    @Operation(summary = "Confirm TOTP enrolment",
        description = "Verifies the first code, sets totp_confirmed_at, and returns 10 "
            + "single-use recovery codes. They are stored hashed and are never retrievable "
            + "again.")
    @PostMapping("/mfa/confirm")
    public ApiResponse<MfaConfirmResponse> confirm(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @Valid @RequestBody MfaConfirmRequest request) {

        return ApiResponse.success("Two-factor authentication is now active. Save these codes.",
            enrolmentService.confirm(bearerToken(authorization), request.code()));
    }

    /**
     * Second factor. A TOTP code or a recovery code, exchanged for a real admin session.
     *
     * <p>Public, like the routes above, because the caller holds a challenge rather than a
     * session. The challenge is retired on success and voided after five failures, so it
     * cannot be replayed or brute-forced.
     */
    @Operation(summary = "Verify TOTP and issue admin tokens",
        description = "Send the challenge token from /login as the bearer token and a 6-digit "
            + "TOTP code, or one of the recovery codes, in the body. Returns a typ=ADMIN "
            + "access token (10 min) and a refresh token (8 h). Five wrong codes void the "
            + "challenge; a correct one retires it.")
    @PostMapping("/mfa/verify")
    public ApiResponse<AdminTokenResponse> verify(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @Valid @RequestBody MfaVerifyRequest request) {

        return ApiResponse.success("Signed in",
            verificationService.verify(bearerToken(authorization), request.code()));
    }

    /**
     * Who the caller is, and every permission they hold.
     *
     * <p>The portal's whole client-side gating model reads from this one response: the sidebar,
     * `usePermission`, and every disabled control (SSOT §8.1).
     */
    @Operation(summary = "Current admin",
        description = "Identity, role and the resolved permission list. Permissions come from "
            + "the caller's own granted authorities, so this cannot disagree with what "
            + "authorization enforces.")
    @GetMapping("/me")
    public ApiResponse<AdminMeResponse> me() {
        return ApiResponse.success("Current admin", sessionService.me());
    }

    /**
     * Exchanges a refresh token for a fresh access token, rotating the refresh token.
     */
    @Operation(summary = "Rotate the admin session",
        description = "Returns a new access token and a NEW refresh token; the one presented "
            + "is revoked as it is spent. Presenting it again is treated as theft and revokes "
            + "every token on the account.")
    @PostMapping("/refresh")
    public ApiResponse<AdminTokenResponse> refresh(@Valid @RequestBody AdminRefreshRequest request) {
        return ApiResponse.success("Session refreshed", sessionService.refresh(request.refreshToken()));
    }

    /**
     * Ends this session.
     *
     * <p>The body is optional: an admin whose portal has already dropped the refresh cookie
     * must still be able to sign out. A logout that can fail is one someone walks away from
     * believing it worked.
     */
    @Operation(summary = "Sign out",
        description = "Revokes the refresh token and the access token's session, so the access "
            + "token stops working immediately rather than when it expires. Other sessions - "
            + "including the mobile app - are untouched.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody(required = false) AdminRefreshRequest request) {

        sessionService.logout(bearerToken(authorization), request == null ? null : request.refreshToken());
        return ApiResponse.success("Signed out", null);
    }

    /**
     * Step-up re-authentication, opening the five-minute window high-tier actions require.
     *
     * <p>Being signed in is not enough to delete a user or export the database. This is the
     * control that bounds what someone can do at an unattended, unlocked laptop (SSOT §10.7).
     */
    @Operation(summary = "Open a sudo window",
        description = "Re-authenticate with a 6-digit TOTP code or your password. Opens a "
            + "5-minute window against THIS session only - a second device does not inherit "
            + "it. High-tier actions answer ADMIN_SUDO_REQUIRED until it is open.")
    @PostMapping("/sudo")
    public ApiResponse<SudoResponse> sudo(@Valid @RequestBody SudoRequest request) {
        return ApiResponse.success("Identity confirmed", sudoService.open(request));
    }

    /**
     * Reissues the ten recovery codes, invalidating the previous set.
     *
     * <p>The first endpoint gated by {@link RequiresSudo}: a fresh set of codes is a permanent
     * bypass of the second factor, so holding a live session is not enough to mint one.
     */
    @Operation(summary = "Regenerate recovery codes",
        description = "Returns ten NEW single-use codes and invalidates every previous one, "
            + "spent or not. Requires an open sudo window (POST /sudo) - without it the request "
            + "is refused with ADMIN_SUDO_REQUIRED. Shown once; stored hashed.")
    @RequiresSudo
    @PostMapping("/mfa/recovery-codes")
    public ApiResponse<RecoveryCodesResponse> regenerateRecoveryCodes() {
        return ApiResponse.success("Save these codes. Your previous codes no longer work.",
            recoveryCodeService.regenerate());
    }

    /**
     * Pulls the token out of the header.
     *
     * <p>Returns null rather than throwing on a malformed header, so a missing and a malformed
     * credential produce the same "session expired" answer from one place, instead of two
     * differently-shaped errors.
     */
    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length());
    }
}
