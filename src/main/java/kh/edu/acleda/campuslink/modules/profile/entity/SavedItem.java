package kh.edu.acleda.campuslink.modules.profile.entity;

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
import java.time.LocalDateTime;
import java.util.UUID;
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
@Table(name = "saved_items", uniqueConstraints = {@UniqueConstraint(name = "uk_saved_items_user_item", columnNames = {"user_id", "item_type", "item_id"})})
@AttributeOverride(name = "id", column = @Column(name = "saved_item_id", nullable = false, updatable = false))
public class SavedItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(name = "item_type", nullable = false, length = 80)
    private String itemType;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt;

}
