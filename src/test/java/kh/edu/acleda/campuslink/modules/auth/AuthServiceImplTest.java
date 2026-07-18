package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.dto.LoginRequest;
import kh.edu.acleda.campuslink.modules.auth.dto.RegisterRequest;
import kh.edu.acleda.campuslink.modules.auth.dto.RefreshTokenRequest;
import kh.edu.acleda.campuslink.modules.auth.dto.RefreshTokenRotation;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.modules.auth.repository.SocialAccountRepository;
import kh.edu.acleda.campuslink.modules.auth.security.AuthRateLimitService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.UserService;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserService userService;
    @Mock
    private GoogleTokenVerifier googleTokenVerifier;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private OtpService otpService;
    @Mock
    private SocialAccountRepository socialAccountRepository;
    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;
    @Mock
    private AuthRateLimitService authRateLimitService;
    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void registerCreatesPendingUnverifiedLocalUserAndIssuesRegisterOtp() {
        RegisterRequest request = new RegisterRequest(
            "Sophea Chan",
            "sophea_chan",
            "sophea@example.com",
            "012345678",
            "password123",
            "ACLEDA University",
            null,
            null
        );
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User user = userCaptor.getValue();
        assertThat(user.getEmail()).isEqualTo(request.email());
        assertThat(user.getUsername()).isEqualTo(request.username());
        assertThat(user.getPhoneNumber()).isEqualTo(request.phoneNumber());
        assertThat(user.getUniversityName()).isEqualTo(request.universityName());
        assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(user.getWorkStatus()).isEqualTo(WorkStatus.STUDENT);
        assertThat(user.getPreferredLanguage()).isEqualTo(Language.ENGLISH);
        assertThat(user.isEmailVerified()).isFalse();
        verify(otpService).issue(user, OtpPurpose.REGISTER);
    }

    @Test
    void registerSavesRequestedPreferredLanguageWhenProvided() {
        RegisterRequest request = new RegisterRequest(
            "Sophea Chan",
            "sophea_kh",
            "sopheakh@example.com",
            "012345679",
            "password123",
            "ACLEDA University",
            null,
            Language.KHMER
        );
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPreferredLanguage()).isEqualTo(Language.KHMER);
    }

    @Test
    void registerRejectsWeakPassword() {
        RegisterRequest request = new RegisterRequest(
            "Sophea Chan",
            "sophea_weak",
            "weak@example.com",
            "012345680",
            "password",
            "ACLEDA University",
            null,
            null
        );
        org.mockito.Mockito.doThrow(new ApiException("Weak password", AuthErrorCode.AUTH_WEAK_PASSWORD))
            .when(passwordPolicyValidator).validate(request.password());

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(AuthErrorCode.AUTH_WEAK_PASSWORD);
    }

    @Test
    void loginRejectsPendingUserBeforePasswordAuthentication() {
        User user = new User();
        user.setEmail("pending@example.com");
        user.setPasswordHash("hash");
        user.setAccountStatus(AccountStatus.PENDING);
        user.setEmailVerified(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "password123")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("verify your email");

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void loginRejectsBannedUserBeforePasswordAuthentication() {
        User user = new User();
        user.setEmail("banned@example.com");
        user.setPasswordHash("hash");
        user.setAccountStatus(AccountStatus.BANNED);
        user.setEmailVerified(true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "password123")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("banned");

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void loginRejectsDeletedUserBeforePasswordAuthentication() {
        User user = new User();
        user.setEmail("deleted@example.com");
        user.setPasswordHash("hash");
        user.setAccountStatus(AccountStatus.DELETED);
        user.setEmailVerified(true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "password123")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("deleted");

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void loginDoesNotOverwritePreferredLanguage() {
        User user = activeUser("active@example.com");
        user.setPasswordHash("hash");
        user.setPreferredLanguage(Language.KHMER);
        Authentication authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), "password123");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.generateAccessToken(user.getEmail(), user.getTokenVersion())).thenReturn("access-token");
        when(refreshTokenService.create(any(User.class), any(), any(), any())).thenReturn("refresh-token");

        authService.login(new LoginRequest(user.getEmail(), "password123"));

        assertThat(user.getPreferredLanguage()).isEqualTo(Language.KHMER);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void refreshTokenDoesNotOverwritePreferredLanguage() {
        User user = activeUser("active@example.com");
        user.setPreferredLanguage(Language.KHMER);
        when(refreshTokenService.rotate(any(), any(), any(), any()))
            .thenReturn(new RefreshTokenRotation(user, "new-refresh-token"));
        when(jwtService.generateAccessToken(user.getEmail(), user.getTokenVersion())).thenReturn("new-access-token");

        authService.refreshToken(new RefreshTokenRequest("old-refresh-token"));

        assertThat(user.getPreferredLanguage()).isEqualTo(Language.KHMER);
        verify(userRepository, never()).save(any(User.class));
    }

    private User activeUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }
}
