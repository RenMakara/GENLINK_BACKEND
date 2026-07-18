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
import kh.edu.acleda.campuslink.modules.profile.enums.SocialPlatform;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One link per platform per user; the URL is user content, stored as written. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_social_links",
    uniqueConstraints = {@UniqueConstraint(name = "uk_user_social_links_user_platform", columnNames = {"user_id", "platform"})})
@AttributeOverride(name = "id", column = @Column(name = "social_link_id", nullable = false, updatable = false))
public class UserSocialLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 40)
    private SocialPlatform platform;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

}
