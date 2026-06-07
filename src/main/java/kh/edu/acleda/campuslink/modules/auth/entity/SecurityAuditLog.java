package kh.edu.acleda.campuslink.modules.auth.entity;

import jakarta.persistence.*;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "security_audit_logs")
@Getter
@Setter
public class SecurityAuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String eventType;

    @Column
    private String email;

    @Column
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Column
    private String deviceId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column
    private String status;

    @Column
    private Boolean success;

    @Column
    private String failureReason;
}
