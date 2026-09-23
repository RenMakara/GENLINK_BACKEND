package kh.edu.acleda.campuslink.modules.admin.user;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserSummaryResponse;
import kh.edu.acleda.campuslink.modules.admin.user.repository.AdminUserListRepository;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for the read-only admin user directory. */
@Service
@RequiredArgsConstructor
public class AdminUserListService {

    private final AdminUserListRepository adminUserListRepository;
    private final AdminUserRepository adminUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> list(AdminUserListCriteria criteria) {
        Pageable pageable = PageRequest.of(criteria.page(), criteria.size());
        Page<User> users = adminUserListRepository.findPage(criteria, pageable);
        Collection<UUID> userIds = users.getContent().stream().map(User::getId).toList();

        Map<UUID, String> adminRoleNames = userIds.isEmpty()
            ? Map.of()
            : adminUserRepository.findByUserIdIn(userIds).stream()
                .filter(AdminUser::isUsable)
                .filter(admin -> admin.getAdminRole() != null)
                .collect(Collectors.toUnmodifiableMap(
                    admin -> admin.getUser().getId(),
                    admin -> admin.getAdminRole().getName(),
                    (first, ignored) -> first));

        Map<UUID, java.time.Instant> lastActiveAt = userIds.isEmpty()
            ? Map.of()
            : refreshTokenRepository.findLastActiveByUserIdIn(userIds).stream()
                .filter(activity -> activity.getUserId() != null)
                .filter(activity -> activity.getLastActiveAt() != null)
                .collect(Collectors.toUnmodifiableMap(
                    RefreshTokenRepository.LastActiveProjection::getUserId,
                    RefreshTokenRepository.LastActiveProjection::getLastActiveAt,
                    (first, ignored) -> first));

        Function<User, AdminUserSummaryResponse> mapper = user -> new AdminUserSummaryResponse(
            user.getId(),
            user.getFullName(),
            user.getUsername(),
            user.getEmail(),
            user.getAvatarUrl(),
            user.isEmailVerified(),
            user.getAccountStatus(),
            adminRoleNames.get(user.getId()),
            user.getUniversityName(),
            user.getWorkStatus(),
            user.getLevel(),
            user.getTotalXp(),
            user.getCreatedAt(),
            lastActiveAt.get(user.getId())
        );

        return PageResponse.from(users, mapper);
    }
}
