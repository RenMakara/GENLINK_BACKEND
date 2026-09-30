package kh.edu.acleda.campuslink.modules.community.repository;

import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.MessageRead;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReadRepository extends JpaRepository<MessageRead, UUID> {

    /** Idempotency guard for marking a message read — unique (message, user) already backs it. */
    boolean existsByMessageIdAndUserId(UUID messageId, UUID userId);

    /** The viewer's read high-water in a room: their most recently read message. */
    Optional<MessageRead> findFirstByUserIdAndMessage_ChatRoomIdOrderByMessage_CreatedAtDesc(
        UUID userId, UUID chatRoomId);
}
