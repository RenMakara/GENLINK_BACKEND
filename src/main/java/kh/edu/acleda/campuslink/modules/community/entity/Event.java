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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import kh.edu.acleda.campuslink.shared.enums.Language;
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
@Table(name = "events")
@AttributeOverride(name = "id", column = @Column(name = "event_id", nullable = false, updatable = false))
public class Event extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = true)
    private User organizer;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "cover_image_url", columnDefinition = "TEXT")
    private String coverImageUrl;

    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    @Column(name = "location_name", length = 180)
    private String locationName;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "attendee_limit")
    private Integer attendeeLimit;

    @Column(name = "attendee_count", nullable = false)
    private Integer attendeeCount;

    /** Paid RSVP is settled against the demo wallet. Simulation only, never real money. */
    @Column(name = "is_paid", nullable = false)
    private Boolean isPaid;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private EventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 20)
    private Language language;

    @Column(name = "deleted_at")
    private Instant deletedAt;

}
