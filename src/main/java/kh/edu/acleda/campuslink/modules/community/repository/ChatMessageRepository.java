package kh.edu.acleda.campuslink.modules.community.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** Message history for a room (paginated; deleted messages excluded). */
    Page<ChatMessage> findByChatRoomIdAndDeletedAtIsNull(UUID chatRoomId, Pageable pageable);

    /** A single live message scoped to its room — used to validate reply targets and deletes. */
    Optional<ChatMessage> findByIdAndChatRoomIdAndDeletedAtIsNull(UUID id, UUID chatRoomId);

    /** The room's latest live message — the chat-list preview and the read high-water target. */
    Optional<ChatMessage> findFirstByChatRoomIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID chatRoomId);

    /** Unread = live messages from other people newer than the viewer's read high-water. */
    long countByChatRoomIdAndDeletedAtIsNullAndSenderIdNotAndCreatedAtAfter(
        UUID chatRoomId, UUID senderId, Instant after);

    /** Soft delete all active messages in a room (clearing conversation history). */
    @Modifying
    @Query("UPDATE ChatMessage m SET m.deletedAt = :deletedAt WHERE m.chatRoom.id = :chatRoomId AND m.deletedAt IS NULL")
    int softDeleteAllByChatRoomId(@Param("chatRoomId") UUID chatRoomId, @Param("deletedAt") Instant deletedAt);
}
