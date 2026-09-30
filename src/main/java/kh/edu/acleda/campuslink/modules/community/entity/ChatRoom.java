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
import kh.edu.acleda.campuslink.modules.community.enums.ChatType;
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
@Table(name = "chat_rooms")
@AttributeOverride(name = "id", column = @Column(name = "chat_room_id", nullable = false, updatable = false))
public class ChatRoom extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_type", nullable = false, length = 50)
    private ChatType chatType;

    @Column(name = "name", length = 120)
    private String name;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = true)
    private User createdByUser;

    /** The room's pinned message, or null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pinned_message_id", nullable = true)
    private ChatMessage pinnedMessage;

}
