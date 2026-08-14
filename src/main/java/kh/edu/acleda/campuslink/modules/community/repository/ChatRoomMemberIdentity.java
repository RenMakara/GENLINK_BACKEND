package kh.edu.acleda.campuslink.modules.community.repository;

import java.util.UUID;

public interface ChatRoomMemberIdentity {
    UUID getUserId();
    String getEmail();
}
