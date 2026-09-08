package kh.edu.acleda.campuslink.modules.community.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "event_rsvps", uniqueConstraints = {@UniqueConstraint(name = "uk_event_rsvps_event_user", columnNames = {"event_id", "user_id"})})
@AttributeOverride(name = "id", column = @Column(name = "rsvp_id", nullable = false, updatable = false))
public class EventRsvp extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = true)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private RsvpStatus status;

    @Column(name = "checked_in", nullable = false)
    private Boolean checkedIn;

    @Column(name = "qr_code", length = 255)
    private String qrCode;

    @Column(name = "full_name", length = 180)
    private String fullName;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "university", length = 180)
    private String university;

    @Column(name = "faculty", length = 180)
    private String faculty;

    @Column(name = "study_year", length = 80)
    private String studyYear;

    @Builder.Default
    @Column(name = "terms_accepted", nullable = false)
    private Boolean termsAccepted = false;

}
