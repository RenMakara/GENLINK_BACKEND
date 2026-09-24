package kh.edu.acleda.campuslink.modules.admin.sanction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.sanction.entity.UserSanction;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionType;
import kh.edu.acleda.campuslink.modules.admin.sanction.repository.UserSanctionRepository;
import kh.edu.acleda.campuslink.modules.auth.RefreshTokenService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class SuspensionExpiryServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-24T04:00:00Z");

    @Mock
    private UserSanctionRepository userSanctionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenService refreshTokenService;

    private User targetReference;
    private SuspensionExpiryService service;

    @BeforeEach
    void setUp() {
        targetReference = org.mockito.Mockito.mock(User.class);
        lenient().when(targetReference.getId()).thenReturn(USER_ID);
        service = new SuspensionExpiryService(
            userSanctionRepository, userRepository, refreshTokenService);
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "PENDING"})
    void expiredSuspensionRestoresTheCapturedStatus(AccountStatus previousStatus) {
        User target = user(AccountStatus.SUSPENDED, 4);
        UserSanction sanction = expiredSuspension(previousStatus);
        when(userSanctionRepository.findExpired(NOW)).thenReturn(List.of(sanction));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(target));

        int lifted = service.expireSuspensions(NOW);

        assertThat(lifted).isEqualTo(1);
        assertThat(target.getAccountStatus()).isEqualTo(previousStatus);
        assertThat(target.getTokenVersion()).isEqualTo(5);
        assertThat(sanction.getLiftedAt()).isEqualTo(NOW);
        assertThat(sanction.getLiftReason()).isEqualTo("Suspension expired automatically");
        verify(refreshTokenService).revoke(target);
        verify(userRepository).save(target);
        verify(userSanctionRepository).save(sanction);
    }

    @Test
    void runningTheExpirySweepTwiceDoesNotMutateTheSameSanctionTwice() {
        User target = user(AccountStatus.SUSPENDED, 1);
        UserSanction sanction = expiredSuspension(AccountStatus.ACTIVE);
        when(userSanctionRepository.findExpired(NOW)).thenReturn(List.of(sanction));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(target));

        assertThat(service.expireSuspensions(NOW)).isEqualTo(1);
        assertThat(service.expireSuspensions(NOW)).isEqualTo(0);

        verify(userRepository).save(target);
        verify(userSanctionRepository).save(sanction);
        verify(refreshTokenService).revoke(target);
    }

    @Test
    void anExpiredSuspensionDoesNotDowngradeAUserBannedLater() {
        User target = user(AccountStatus.BANNED, 7);
        UserSanction sanction = expiredSuspension(AccountStatus.ACTIVE);
        when(userSanctionRepository.findExpired(NOW)).thenReturn(List.of(sanction));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(target));

        assertThat(service.expireSuspensions(NOW)).isEqualTo(1);

        assertThat(target.getAccountStatus()).isEqualTo(AccountStatus.BANNED);
        assertThat(target.getTokenVersion()).isEqualTo(7);
        assertThat(sanction.getLiftedAt()).isEqualTo(NOW);
        verify(userRepository, never()).save(target);
        verify(refreshTokenService, never()).revoke(any(User.class));
    }

    @Test
    void candidatesThatAreNotActuallyExpiredAreIgnored() {
        UserSanction sanction = UserSanction.builder()
            .user(targetReference)
            .type(SanctionType.SUSPENSION)
            .reason("Temporary restriction for review")
            .previousStatus(AccountStatus.ACTIVE)
            .startsAt(NOW.minusSeconds(7200))
            .expiresAt(NOW.plusSeconds(1))
            .build();
        when(userSanctionRepository.findExpired(NOW)).thenReturn(List.of(sanction));

        assertThat(service.expireSuspensions(NOW)).isZero();
        verify(userRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(userSanctionRepository, never()).save(any(UserSanction.class));
    }

    @Test
    void nullRunTimeIsRejectedBeforeQueryingTheDatabase() {
        assertThatThrownBy(() -> service.expireSuspensions(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("now must not be null");

        verify(userSanctionRepository, never()).findExpired(any(Instant.class));
    }

    private UserSanction expiredSuspension(AccountStatus previousStatus) {
        return UserSanction.builder()
            .user(targetReference)
            .type(SanctionType.SUSPENSION)
            .reason("Temporary restriction for review")
            .previousStatus(previousStatus)
            .startsAt(NOW.minusSeconds(7200))
            .expiresAt(NOW.minusSeconds(1))
            .build();
    }

    private User user(AccountStatus status, int tokenVersion) {
        User user = new User();
        user.setAccountStatus(status);
        user.setTokenVersion(tokenVersion);
        user.setEmail("target@example.com");
        user.setFullName("Target User");
        user.setUsername("target");
        return user;
    }
}
