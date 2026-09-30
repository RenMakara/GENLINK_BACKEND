package kh.edu.acleda.campuslink.modules.admin.audit.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One privileged admin action, successful or not. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "admin_audit_logs")
@AttributeOverride(name = "id", column = @Column(name = "admin_audit_log_id", nullable = false, updatable = false))
public class AdminAuditLog extends BaseEntity {

    /** Null where there is no admin to attribute the action to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id")
    private AdminUser adminUser;

    /** What was done, e.g. {@code USER_BAN}. */
    @Column(name = "action", nullable = false, length = 80)
    private String action;

    /** What it was done to, e.g. {@code USER} or {@code POST}. */
    @Column(name = "target_type", length = 40)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    /** State before the change, for update-shaped actions. Null for creates and reads. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json")
    private String beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json")
    private String afterJson;

    /** The admin's stated justification. Required by medium- and high-tier actions. */
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    /** Correlation id from {@code RequestId}, tying this row to the request's log lines. */
    @Column(name = "request_id", length = 64)
    private String requestId;

    /** Groups the rows of one bulk operation. */
    @Column(name = "batch_id")
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuditStatus status;

    /** Populated on {@link AuditStatus#FAILURE}; the error code the caller would have seen. */
    @Column(name = "error_code", length = 60)
    private String errorCode;
}
