package kh.edu.acleda.campuslink.modules.community.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.ChatRoom;
import kh.edu.acleda.campuslink.modules.community.enums.ChatType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    /** The existing 1:1 room shared by two users, if any. */
    @Query("SELECT r FROM ChatRoom r WHERE r.chatType = :type "
        + "AND EXISTS (SELECT 1 FROM ChatRoomMember a WHERE a.chatRoom = r AND a.user.id = :userA) "
        + "AND EXISTS (SELECT 1 FROM ChatRoomMember b WHERE b.chatRoom = r AND b.user.id = :userB)")
    Optional<ChatRoom> findPrivateRoomBetween(@Param("type") ChatType type,
                                              @Param("userA") UUID userA,
                                              @Param("userB") UUID userB);

    /** Bumps last-activity so the chat list can sort by it. */
    @Modifying
    @Query("UPDATE ChatRoom r SET r.updatedAt = :now WHERE r.id = :roomId")
    void touch(@Param("roomId") UUID roomId, @Param("now") Instant now);
}
