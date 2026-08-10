package kh.edu.acleda.campuslink.modules.admin.rbac.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A staff role. Seven are seeded by migration 0041; more can be added as data.
 *
 * <p>Roles are data so that changing who can do what is an INSERT rather than a deploy.
 * Permissions, by contrast, are code - see {@code Permission}.
 *
 * <p>{@code SUPER_ADMIN} is a row here like any other, but carries <strong>no</strong>
 * {@code role_permissions}: it implicitly holds every permission, resolved in code. Seeding
 * its grants as well would create a second source of truth that drifts the moment a new
 * permission is added.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "admin_roles")
@AttributeOverride(name = "id", column = @Column(name = "admin_role_id", nullable = false, updatable = false))
public class AdminRole extends BaseEntity {

    /** Stable identifier used in code and seeds, e.g. {@code MODERATOR}. Never renamed. */
    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    /** Display name, editable in the role editor. */
    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    /**
     * True for the seven roles shipped with the product. System roles may be renamed and
     * re-permissioned but never deleted - deleting one would strand every admin holding it.
     */
    @Column(name = "is_system", nullable = false)
    private boolean isSystem;
}
