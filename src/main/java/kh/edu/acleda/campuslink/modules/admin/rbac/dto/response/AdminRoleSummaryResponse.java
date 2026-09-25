package kh.edu.acleda.campuslink.modules.admin.rbac.dto.response;

import java.util.UUID;

/** Safe list projection for one admin role. */
public record AdminRoleSummaryResponse(
    UUID roleId,
    String code,
    String name,
    String description,
    boolean isSystem,
    long adminCount
) {
}
