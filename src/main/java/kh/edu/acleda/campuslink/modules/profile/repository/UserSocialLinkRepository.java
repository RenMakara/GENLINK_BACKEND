package kh.edu.acleda.campuslink.modules.profile.repository;

import kh.edu.acleda.campuslink.modules.profile.entity.UserSocialLink;
import kh.edu.acleda.campuslink.modules.profile.enums.SocialPlatform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSocialLinkRepository extends JpaRepository<UserSocialLink, UUID> {

    List<UserSocialLink> findByUserIdOrderByPlatformAsc(UUID userId);

    Optional<UserSocialLink> findByUserIdAndPlatform(UUID userId, SocialPlatform platform);
}
