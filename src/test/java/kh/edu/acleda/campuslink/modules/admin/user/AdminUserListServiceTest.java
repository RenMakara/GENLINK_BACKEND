package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserSummaryResponse;
import kh.edu.acleda.campuslink.modules.admin.user.repository.AdminUserListRepository;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class AdminUserListServiceTest {

    private AdminUserListRepository listRepository;
    private AdminUserRepository adminUserRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private AdminUserListService service;

    @BeforeEach
    void setUp() {
        listRepository = mock(AdminUserListRepository.class);
        adminUserRepository = mock(AdminUserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        service = new AdminUserListService(listRepository, adminUserRepository, refreshTokenRepository);
    }

    @Test
    void mapsOnlySafeRowFieldsAndBatchEnrichment() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-01T00:00:00Z");
        Instant lastActiveAt = Instant.parse("2026-09-22T00:00:00Z");
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getFullName()).thenReturn("Makara Ren");
        when(user.getUsername()).thenReturn("makara");
        when(user.getEmail()).thenReturn("makara@example.com");
        when(user.isEmailVerified()).thenReturn(true);
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(user.getUniversityName()).thenReturn("ACME");
        when(user.getLevel()).thenReturn(4);
        when(user.getTotalXp()).thenReturn(250);
        when(user.getCreatedAt()).thenReturn(createdAt);

        AdminRole role = mock(AdminRole.class);
        when(role.getName()).thenReturn("Moderator");
        AdminUser admin = mock(AdminUser.class);
        when(admin.isUsable()).thenReturn(true);
        when(admin.getUser()).thenReturn(user);
        when(admin.getAdminRole()).thenReturn(role);

        RefreshTokenRepository.LastActiveProjection activity =
            mock(RefreshTokenRepository.LastActiveProjection.class);
        when(activity.getUserId()).thenReturn(userId);
        when(activity.getLastActiveAt()).thenReturn(lastActiveAt);

        when(listRepository.findPage(any(), any())).thenReturn(
            new PageImpl<>(List.of(user), PageRequest.of(0, 25), 1));
        when(adminUserRepository.findByUserIdIn(List.of(userId))).thenReturn(List.of(admin));
        when(refreshTokenRepository.findLastActiveByUserIdIn(List.of(userId)))
            .thenReturn(List.of(activity));

        AdminUserListCriteria criteria = AdminUserListCriteria.fromRequest(
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null,
            false, false, 0, 25, null);

        PageResponse<AdminUserSummaryResponse> response = service.list(criteria);
        AdminUserSummaryResponse row = response.content().get(0);

        assertThat(row.userId()).isEqualTo(userId);
        assertThat(row.adminRoleName()).isEqualTo("Moderator");
        assertThat(row.lastActiveAt()).isEqualTo(lastActiveAt);
        assertThat(row.email()).isEqualTo("makara@example.com");
        assertThat(row.totalXp()).isEqualTo(250);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void leavesLastActiveNullWhenRefreshActivityHasNoTimestamp() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);

        RefreshTokenRepository.LastActiveProjection activity =
            mock(RefreshTokenRepository.LastActiveProjection.class);
        when(activity.getUserId()).thenReturn(userId);
        when(activity.getLastActiveAt()).thenReturn(null);

        when(listRepository.findPage(any(), any())).thenReturn(
            new PageImpl<>(List.of(user), PageRequest.of(0, 25), 1));
        when(adminUserRepository.findByUserIdIn(List.of(userId))).thenReturn(List.of());
        when(refreshTokenRepository.findLastActiveByUserIdIn(List.of(userId)))
            .thenReturn(List.of(activity));

        AdminUserListCriteria criteria = AdminUserListCriteria.fromRequest(
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null,
            false, false, 0, 25, null);

        AdminUserSummaryResponse row = service.list(criteria).content().get(0);

        assertThat(row.lastActiveAt()).isNull();
    }
}
