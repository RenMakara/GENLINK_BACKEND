package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.dto.LoginRequest;
import kh.edu.acleda.campuslink.modules.auth.dto.RegisterRequest;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpType;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.UserService;
import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.modules.user.entity.UserStatus;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
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
    private FirebaseAuthService firebaseAuthService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private OtpService otpService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void registerCreatesPendingUnverifiedLocalUserAndIssuesRegisterOtp() {
        RegisterRequest request = new RegisterRequest(
            "Sophea Chan",
            "sophea@example.com",
            "012345678",
            "password123",
            "ACLEDA University"
        );
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User user = userCaptor.getValue();
        assertThat(user.getEmail()).isEqualTo(request.email());
        assertThat(user.getPhone()).isEqualTo(request.phone());
        assertThat(user.getUniversity()).isEqualTo(request.university());
        assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(user.isEmailVerified()).isFalse();
        verify(otpService).issue(user, OtpType.REGISTER);
    }

    @Test
    void loginRejectsPendingUserBeforePasswordAuthentication() {
        User user = new User();
        user.setEmail("pending@example.com");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.PENDING);
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
        user.setStatus(UserStatus.BANNED);
        user.setEmailVerified(true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "password123")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("banned");

        verify(authenticationManager, never()).authenticate(any());
    }
}
