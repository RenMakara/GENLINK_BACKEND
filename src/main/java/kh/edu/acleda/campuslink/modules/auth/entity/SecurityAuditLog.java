package kh.edu.acleda.campuslink.modules.auth.entity;

import jakarta.persistence.*;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "security_audit_logs")
@Getter
@Setter
public class SecurityAuditLog extends BaseEntity {

    @Column(nullable = false)
    private String eventType;

    @Column
    private String email;

    @Column
    private String ipAddress;

    @Column
    private String deviceId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column
    private String status;
}
