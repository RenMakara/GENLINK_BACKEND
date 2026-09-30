package kh.edu.acleda.campuslink.modules.admin.rbac;

import java.util.UUID;

/** The minimum needed to decide what a staff account may do: which admin profile, and which role. */
public record AdminIdentity(UUID adminUserId, UUID roleId, String roleCode) {

    /** The "not a staff account" answer, cached like any other. */
    public static final AdminIdentity NONE = new AdminIdentity(null, null, null);

    public boolean isAdmin() {
        return adminUserId != null;
    }
}
