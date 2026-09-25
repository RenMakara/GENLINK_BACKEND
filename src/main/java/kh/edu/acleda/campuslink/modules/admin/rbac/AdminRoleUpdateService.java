package kh.edu.acleda.campuslink.modules.admin.rbac;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminRolePermissionUpdateRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminRoleUpdateRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminRoleDetailResponse;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.RolePermission;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminRoleRepository;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.RolePermissionRepository;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional role metadata and whole-set permission updates for the Admin portal (ADM-102). */
@Service
@RequiredArgsConstructor
public class AdminRoleUpdateService {

    private static final String TARGET_TYPE_ROLE = "ADMIN_ROLE";
    private static final String ACTION_PERMISSION_UPDATE = "ROLE_PERMISSION_UPDATE";
    private static final String ACTION_ROLE_UPDATE = "ROLE_UPDATE";
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    private final AdminRoleRepository adminRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AdminPermissionCache adminPermissionCache;
    private final AdminRoleReadService adminRoleReadService;
    private final AdminAuditService adminAuditService;

    /** Replaces every stored grant in one transaction, rather than applying an add/remove diff. */
    @Transactional
    public AdminRoleDetailResponse replacePermissions(
        UUID roleId, AdminRolePermissionUpdateRequest request) {
        String reason = requireReason(request == null ? null : request.reason());
        try {
            AdminRole role = findRole(roleId);
            if (SuperAdminGuard.SUPER_ADMIN.equals(role.getCode())) {
                throw new ApiException(
                    "SUPER_ADMIN permissions are implicit and cannot be edited.",
                    AdminErrorCode.ADMIN_SUPER_ADMIN_PERMISSIONS_IMMUTABLE);
            }

            List<String> replacement = validatePermissionCodes(
                request == null ? null : request.permissions());
            List<String> before = List.copyOf(rolePermissionRepository.findPermissionCodesByRoleId(roleId));

            rolePermissionRepository.deleteByAdminRoleId(roleId);
            rolePermissionRepository.saveAll(replacement.stream()
                .map(code -> RolePermission.builder().adminRole(role).permissionCode(code).build())
                .toList());
            adminPermissionCache.evictRoleAuthorities(roleId);

            recordSnapshotSuccess(
                ACTION_PERMISSION_UPDATE,
                roleId,
                reason,
                new RolePermissionSnapshot(role.getCode(), before),
                new RolePermissionSnapshot(role.getCode(), replacement));
            return adminRoleReadService.getRole(roleId);
        } catch (NotFoundException ex) {
            recordFailure(ACTION_PERMISSION_UPDATE, roleId, reason, NOT_FOUND);
            throw ex;
        } catch (ApiException ex) {
            recordFailure(ACTION_PERMISSION_UPDATE, roleId, reason, ex.getErrorCode());
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_PERMISSION_UPDATE, roleId, reason, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    /** Renames or re-describes a role without changing its stable code or system flag. */
    @Transactional
    public AdminRoleDetailResponse updateMetadata(UUID roleId, AdminRoleUpdateRequest request) {
        String reason = requireReason(request == null ? null : request.reason());
        try {
            AdminRole role = findRole(roleId);
            RoleMetadataSnapshot before = metadataSnapshot(role);
            String name = requireName(request == null ? null : request.name());
            role.setName(name);
            role.setDescription(normalizeDescription(request == null ? null : request.description()));
            adminRoleRepository.save(role);

            recordSnapshotSuccess(
                ACTION_ROLE_UPDATE,
                roleId,
                reason,
                before,
                metadataSnapshot(role));
            return adminRoleReadService.getRole(roleId);
        } catch (NotFoundException ex) {
            recordFailure(ACTION_ROLE_UPDATE, roleId, reason, NOT_FOUND);
            throw ex;
        } catch (ApiException ex) {
            recordFailure(ACTION_ROLE_UPDATE, roleId, reason, ex.getErrorCode());
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_ROLE_UPDATE, roleId, reason, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    private AdminRole findRole(UUID roleId) {
        return adminRoleRepository.findById(roleId)
            .orElseThrow(() -> new NotFoundException("Admin role not found with id: " + roleId));
    }

    private static List<String> validatePermissionCodes(List<String> requested) {
        if (requested == null) {
            throw new ApiException("permissions is required", VALIDATION_ERROR);
        }
        if (requested.size() > Permission.values().length) {
            throw new ApiException(
                "permissions must not contain more than " + Permission.values().length + " codes",
                VALIDATION_ERROR);
        }

        Set<String> normalized = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        for (String rawCode : requested) {
            String code = rawCode == null ? null : rawCode.trim();
            if (code == null || Permission.fromCode(code).isEmpty()) {
                unknown.add(String.valueOf(rawCode));
                continue;
            }
            if (!normalized.add(code)) {
                duplicates.add(code);
            }
        }
        if (!unknown.isEmpty()) {
            throw new ApiException(
                "Unknown permission code(s): " + String.join(", ", unknown), VALIDATION_ERROR);
        }
        if (!duplicates.isEmpty()) {
            throw new ApiException(
                "permissions must not contain duplicates: "
                    + String.join(", ", duplicates), VALIDATION_ERROR);
        }

        // Persist and audit in the enum's stable order. The response/read path uses the same
        // order, so a request is deterministic regardless of checkbox traversal order.
        return Permission.values().length == normalized.size()
            ? Permission.allCodes()
            : Arrays.stream(Permission.values())
                .filter(permission -> normalized.contains(permission.getCode()))
                .map(Permission::getCode)
                .toList();
    }

    private void recordSnapshotSuccess(
        String action, UUID roleId, String reason, Object before, Object after) {
        adminAuditService.record(AdminAuditEvent.builder()
            .action(action)
            .targetType(TARGET_TYPE_ROLE)
            .targetId(roleId)
            .reason(reason)
            .beforeJson(adminAuditService.toJson(before))
            .afterJson(adminAuditService.toJson(after))
            .build());
    }

    private void recordFailure(String action, UUID roleId, String reason, String errorCode) {
        adminAuditService.recordFailure(action, TARGET_TYPE_ROLE, roleId, reason, errorCode);
    }

    private static String requireReason(String reason) {
        String normalized = reason == null ? null : reason.trim();
        if (normalized == null || normalized.length() < 10 || normalized.length() > 2000) {
            throw new ApiException("reason: size must be between 10 and 2000", VALIDATION_ERROR);
        }
        return normalized;
    }

    private static String requireName(String name) {
        String normalized = name == null ? null : name.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 80) {
            throw new ApiException("name: must contain between 1 and 80 characters", VALIDATION_ERROR);
        }
        return normalized;
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static RoleMetadataSnapshot metadataSnapshot(AdminRole role) {
        return new RoleMetadataSnapshot(role.getCode(), role.getName(), role.getDescription(), role.isSystem());
    }

    private record RolePermissionSnapshot(String roleCode, List<String> permissions) {
        private RolePermissionSnapshot {
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
        }
    }

    private record RoleMetadataSnapshot(
        String roleCode, String name, String description, boolean isSystem) {
    }
}
