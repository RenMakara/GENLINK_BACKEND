package kh.edu.acleda.campuslink.modules.user.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import kh.edu.acleda.campuslink.shared.enums.Language;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "users")
@AttributeOverrides({
    @AttributeOverride(name = "id", column = @Column(name = "user_id")),
    @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false)),
    @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at"))
})
public class User extends BaseEntity {

    @Setter
    @Column(name = "email", nullable = false, unique = true, length = 180)
    private String email;

    @Setter
    @Column(name = "password_hash", columnDefinition = "TEXT")
    private String passwordHash;

    @Setter
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Setter
    @Column(name = "username", nullable = false, unique = true, length = 60)
    private String username;

    @Setter
    @Column(name = "phone_number", unique = true, length = 30)
    private String phoneNumber;

    @Setter
    @Column(name = "university_name", length = 180)
    private String universityName;

    @Setter
    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Setter
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 30)
    private Gender gender;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "work_status", nullable = false, length = 50)
    private WorkStatus workStatus = WorkStatus.STUDENT;

    @Setter
    @Column(name = "location", length = 120)
    private String location;

    @Setter
    @Column(name = "google_id")
    private String googleId;

    /**
     * Embedded in every access token as the "tv" claim. Incrementing it invalidates
     * all access tokens already issued for this user.
     */
    @Setter
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    @Setter
    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    /** ID of an app-bundled profile cover preset (0 through 9). */
    @Setter
    @Column(name = "cover_preset", nullable = false)
    private int coverPreset = 0;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 30)
    private AccountStatus accountStatus = AccountStatus.PENDING;

    @Setter
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Setter
    @Column(name = "total_xp", nullable = false)
    private int totalXp = 0;

    @Setter
    @Column(name = "level", nullable = false)
    private int level = 1;

    @Setter
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 20)
    private Language preferredLanguage = Language.ENGLISH;

    /** Null-safe: rows created before token_version existed read back as 0. */
    public int getTokenVersion() {
        return tokenVersion == null ? 0 : tokenVersion;
    }

    /** Call when every existing access token for this user must stop working. */
    public void incrementTokenVersion() {
        this.tokenVersion = getTokenVersion() + 1;
    }

    public String getPhone() {
        return phoneNumber;
    }

    public void setPhone(String phone) {
        this.phoneNumber = phone;
    }

    public String getUniversity() {
        return universityName;
    }

    public void setUniversity(String university) {
        this.universityName = university;
    }

    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    /**
     * Exposes only whether a local password is configured. The hash itself must
     * never leave the backend or be included in an API response.
     */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public void setActive(boolean active) {
        this.accountStatus = active ? AccountStatus.ACTIVE : AccountStatus.PENDING;
    }

    public UserStatus getStatus() {
        return UserStatus.valueOf(accountStatus.name());
    }

    public void setStatus(UserStatus status) {
        this.accountStatus = AccountStatus.valueOf(status.name());
    }

    public void setPreferredLanguage(Language preferredLanguage) {
        this.preferredLanguage = preferredLanguage == null ? Language.ENGLISH : preferredLanguage;
    }
}
