package kh.edu.acleda.campuslink.modules.community.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.ChatRoomMember;
import kh.edu.acleda.campuslink.modules.community.enums.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, UUID> {

    Optional<ChatRoomMember> findByChatRoomIdAndUserId(UUID chatRoomId, UUID userId);

    List<ChatRoomMember> findByChatRoomIdAndStatus(UUID chatRoomId, MemberStatus status);

    List<ChatRoomMember> findByUserIdAndStatus(UUID userId, MemberStatus status);

    @Query("SELECT m.user.id AS userId, m.user.email AS email FROM ChatRoomMember m WHERE m.chatRoom.id = :chatRoomId AND m.status = :status")
    List<ChatRoomMemberIdentity> findActiveMemberIdentities(@Param("chatRoomId") UUID chatRoomId, @Param("status") MemberStatus status);

    /** The viewer's chat list: rooms they are still in, most-recently-active first. */
    @Query(value = "SELECT m FROM ChatRoomMember m JOIN m.chatRoom r "
        + "WHERE m.user.id = :userId AND m.status = :status ORDER BY r.updatedAt DESC",
        countQuery = "SELECT COUNT(m) FROM ChatRoomMember m WHERE m.user.id = :userId AND m.status = :status")
    Page<ChatRoomMember> findMyRooms(@Param("userId") UUID userId,
                                     @Param("status") MemberStatus status,
                                     Pageable pageable);
}
