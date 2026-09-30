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

/** A staff role. */
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

    /** True for the seven roles shipped with the product. */
    @Column(name = "is_system", nullable = false)
    private boolean isSystem;
}
