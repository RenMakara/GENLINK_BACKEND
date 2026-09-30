package kh.edu.acleda.campuslink.modules.admin.rbac;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminPermissionGroupResponse;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminPermissionResponse;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminRoleDetailResponse;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminRoleSummaryResponse;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminRoleRepository;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.RolePermissionRepository;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only application service for the roles and permission catalogue. */
@Service
@RequiredArgsConstructor
public class AdminRoleReadService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final AdminRoleRepository adminRoleRepository;
    private final AdminUserRepository adminUserRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Transactional(readOnly = true)
    public List<AdminRoleSummaryResponse> listRoles() {
        Map<UUID, Long> adminCounts = activeAdminCounts();
        return adminRoleRepository.findAllByOrderByCodeAsc().stream()
            .map(role -> summary(role, adminCounts.getOrDefault(role.getId(), 0L)))
            .toList();
    }

    @Transactional(readOnly = true)
    public AdminRoleDetailResponse getRole(UUID roleId) {
        AdminRole role = adminRoleRepository.findById(roleId)
            .orElseThrow(() -> new NotFoundException("Admin role not found with id: " + roleId));

        long adminCount = adminUserRepository
            .countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull(role.getCode());
        return new AdminRoleDetailResponse(
            role.getId(),
            role.getCode(),
            role.getName(),
            role.getDescription(),
            role.isSystem(),
            adminCount,
            permissionCodes(role));
    }

    @Transactional(readOnly = true)
    public List<AdminPermissionGroupResponse> listPermissions() {
        Map<String, List<AdminPermissionResponse>> grouped = new LinkedHashMap<>();
        for (Permission permission : Permission.values()) {
            grouped.computeIfAbsent(permission.getResource(), ignored -> new java.util.ArrayList<>())
                .add(new AdminPermissionResponse(
                    permission.getCode(),
                    permission.getResource(),
                    permission.getAction(),
                    permission.getDescription()));
        }
        return grouped.entrySet().stream()
            .map(entry -> new AdminPermissionGroupResponse(entry.getKey(), entry.getValue()))
            .toList();
    }

    private Map<UUID, Long> activeAdminCounts() {
        return adminRoleRepository.countActiveAdminsByRole().stream()
            .collect(Collectors.toUnmodifiableMap(
                AdminRoleRepository.ActiveAdminCount::getRoleId,
                count -> count.getAdminCount() == null ? 0L : count.getAdminCount()));
    }

    private static AdminRoleSummaryResponse summary(AdminRole role, long adminCount) {
        return new AdminRoleSummaryResponse(
            role.getId(),
            role.getCode(),
            role.getName(),
            role.getDescription(),
            role.isSystem(),
            adminCount);
    }

    /** SUPER_ADMIN is implicit in code and deliberately has no stored grants. */
    private List<String> permissionCodes(AdminRole role) {
        if (SUPER_ADMIN.equals(role.getCode())) {
            return Permission.allCodes();
        }

        Set<String> storedCodes = rolePermissionRepository.findPermissionCodesByRoleId(role.getId())
            .stream()
            .collect(Collectors.toSet());
        return Arrays.stream(Permission.values())
            .map(Permission::getCode)
            .filter(storedCodes::contains)
            .toList();
    }
}
