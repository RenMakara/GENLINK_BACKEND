package kh.edu.acleda.campuslink.modules.admin.rbac.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One grant: this role holds this permission.
 *
 * <p>{@link #permissionCode} is a plain string, <strong>not</strong> a foreign key, because
 * there is no {@code permissions} table - the catalogue is the {@code Permission} enum. A
 * permission only exists if some {@code @PreAuthorize} enforces it, so storing the catalogue
 * in the database would let the role editor display and grant permissions that do nothing.
 *
 * <p>The cost of that choice is one unguarded drift direction: a stored code whose constant
 * was renamed or removed. {@code PermissionTest} asserts every seeded code resolves, and the
 * permission resolver skips unresolvable codes rather than failing - one stale grant must not
 * lock every admin out.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "role_permissions")
@AttributeOverride(name = "id", column = @Column(name = "role_permission_id", nullable = false, updatable = false))
public class RolePermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_role_id", nullable = false)
    private AdminRole adminRole;

    /** The {@code Permission} enum's code, e.g. {@code user:ban}. */
    @Column(name = "permission_code", nullable = false, length = 64)
    private String permissionCode;
}
