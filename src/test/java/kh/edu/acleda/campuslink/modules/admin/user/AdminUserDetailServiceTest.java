package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.AdminIdentity;
import kh.edu.acleda.campuslink.modules.admin.rbac.AdminPermissionCache;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.admin.sanction.entity.UserSanction;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionSeverity;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionType;
import kh.edu.acleda.campuslink.modules.admin.sanction.repository.UserSanctionRepository;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserDetailResponse;
import kh.edu.acleda.campuslink.modules.profile.dto.response.InterestResponse;
import kh.edu.acleda.campuslink.modules.profile.dto.response.SocialLinkResponse;
import kh.edu.acleda.campuslink.modules.profile.enums.SocialPlatform;
import kh.edu.acleda.campuslink.modules.profile.service.InterestService;
import kh.edu.acleda.campuslink.modules.profile.service.PortfolioService;
import kh.edu.acleda.campuslink.modules.profile.service.SocialLinkService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.Gender;
import kh.edu.acleda.campuslink.modules.user.entity.Role;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ForbiddenException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminUserDetailServiceTest {

    private UserRepository userRepository;
    private AdminUserRepository adminUserRepository;
    private AdminPermissionCache adminPermissionCache;
    private InterestService interestService;
    private SocialLinkService socialLinkService;
    private PortfolioService portfolioService;
    private UserSanctionRepository userSanctionRepository;
    private AdminUserDetailService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        adminUserRepository = mock(AdminUserRepository.class);
        adminPermissionCache = mock(AdminPermissionCache.class);
        interestService = mock(InterestService.class);
        socialLinkService = mock(SocialLinkService.class);
        portfolioService = mock(PortfolioService.class);
        userSanctionRepository = mock(UserSanctionRepository.class);
        service = new AdminUserDetailService(
            userRepository, adminUserRepository, adminPermissionCache, interestService, socialLinkService,
            portfolioService, userSanctionRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mapsProfileAccountCollectionsAndCurrentSanctionWithoutCredentials() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-09-20T00:00:00Z");
        Instant deletedAt = Instant.parse("2026-09-21T00:00:00Z");
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(user.getFullName()).thenReturn("Makara Ren");
        when(user.getUsername()).thenReturn("makara");
        when(user.getAvatarUrl()).thenReturn("https://cdn.example/avatar.png");
        when(user.getBio()).thenReturn("Builder");
        when(user.getDateOfBirth()).thenReturn(LocalDate.of(2000, 1, 2));
        when(user.getGender()).thenReturn(Gender.MALE);
        when(user.getPhoneNumber()).thenReturn("+85512345678");
        when(user.getLocation()).thenReturn("Phnom Penh");
        when(user.getUniversityName()).thenReturn("ACME University");
        when(user.getWorkStatus()).thenReturn(WorkStatus.STUDENT);
        when(user.getPreferredLanguage()).thenReturn(Language.ENGLISH);
        when(user.getCoverPreset()).thenReturn(2);
        when(user.getEmail()).thenReturn("makara@example.com");
        when(user.isEmailVerified()).thenReturn(true);
        when(user.getAuthProvider()).thenReturn(AuthProvider.GOOGLE);
        when(user.getGoogleId()).thenReturn("provider-id-must-not-leave-api");
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(user.getCreatedAt()).thenReturn(createdAt);
        when(user.getUpdatedAt()).thenReturn(updatedAt);
        when(user.getDeletedAt()).thenReturn(deletedAt);
        when(user.getTokenVersion()).thenReturn(4);
        when(user.getRole()).thenReturn(Role.USER);
        when(user.getTotalXp()).thenReturn(250);
        when(user.getLevel()).thenReturn(3);
        when(user.getPasswordHash()).thenReturn("password-hash-must-not-leave-api");

        AdminRole role = mock(AdminRole.class);
        when(role.getName()).thenReturn("Moderator");
        AdminUser adminUser = mock(AdminUser.class);
        when(adminUser.getAdminRole()).thenReturn(role);
        when(adminUserRepository.findByUserIdAndDeletedAtIsNull(userId))
            .thenReturn(Optional.of(adminUser));

        when(interestService.getByUserId(userId, Language.ENGLISH)).thenReturn(List.of(
            new InterestResponse(UUID.randomUUID(), "coding", "Coding", "skills")));
        when(socialLinkService.getByUserId(userId)).thenReturn(List.of(
            new SocialLinkResponse(SocialPlatform.GITHUB, "https://github.com/makara")));
        when(portfolioService.countByUserId(userId)).thenReturn(3L);

        UserSanction sanction = mock(UserSanction.class);
        Instant sanctionStart = Instant.parse("2026-09-15T00:00:00Z");
        when(sanction.isInForce(any(Instant.class))).thenReturn(true);
        when(sanction.getType()).thenReturn(SanctionType.SUSPENSION);
        when(sanction.getSeverity()).thenReturn(SanctionSeverity.HIGH);
        when(sanction.getReason()).thenReturn("Repeated abuse");
        when(sanction.getPreviousStatus()).thenReturn(AccountStatus.ACTIVE);
        when(sanction.getStartsAt()).thenReturn(sanctionStart);
        when(sanction.getExpiresAt()).thenReturn(null);
        when(sanction.getAcknowledgedAt()).thenReturn(null);
        when(userSanctionRepository
            .findByUserIdAndLiftedAtIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(userId))
            .thenReturn(List.of(sanction));

        AdminUserDetailResponse response = service.get(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.phoneNumber()).isEqualTo("+85512345678");
        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(2000, 1, 2));
        assertThat(response.email()).isEqualTo("makara@example.com");
        assertThat(response.googleLinked()).isTrue();
        assertThat(response.tokenVersion()).isEqualTo(4);
        assertThat(response.deletedAt()).isEqualTo(deletedAt);
        assertThat(response.adminRoleName()).isEqualTo("Moderator");
        assertThat(response.interests()).hasSize(1);
        assertThat(response.socialLinks().get(0).platform()).isEqualTo(SocialPlatform.GITHUB);
        assertThat(response.portfolioCount()).isEqualTo(3L);
        assertThat(response.currentSanction().type()).isEqualTo(SanctionType.SUSPENSION);

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);
        assertThat(json)
            .doesNotContain("passwordHash", "password-hash-must-not-leave-api", "googleId",
                "provider-id-must-not-leave-api");
        verify(portfolioService).countByUserId(userId);
    }

    @Test
    void omitsExpiredCurrentSanctions() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(user.getPreferredLanguage()).thenReturn(Language.ENGLISH);
        when(adminUserRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());
        when(interestService.getByUserId(userId, Language.ENGLISH)).thenReturn(List.of());
        when(socialLinkService.getByUserId(userId)).thenReturn(List.of());
        when(portfolioService.countByUserId(userId)).thenReturn(0L);

        UserSanction expired = mock(UserSanction.class);
        when(expired.isInForce(any(Instant.class))).thenReturn(false);
        when(userSanctionRepository
            .findByUserIdAndLiftedAtIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(userId))
            .thenReturn(List.of(expired));

        assertThat(service.get(userId).currentSanction()).isNull();
    }

    @Test
    void missingUserReturnsNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining(userId.toString());
    }

    @Test
    void financeAdminCannotReadTheFullProfileProjection() {
        String email = "finance@example.com";
        when(adminPermissionCache.identityFor(email)).thenReturn(
            new AdminIdentity(UUID.randomUUID(), UUID.randomUUID(), "FINANCE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));

        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
            .isInstanceOf(ForbiddenException.class)
            .hasMessage("Finance administrators cannot access full user profiles")
            .satisfies(exception -> assertThat(((ForbiddenException) exception).getErrorCode())
                .isEqualTo("ADMIN_USER_PROFILE_SCOPE_FORBIDDEN"));
    }
}
