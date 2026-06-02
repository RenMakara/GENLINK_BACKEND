package kh.edu.acleda.campuslink.modules.user.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
@AttributeOverrides({
    @AttributeOverride(name = "id", column = @Column(name = "user_id")),
    @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false)),
    @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at"))
})
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 180)
    private String email;

    @Column(name = "password_hash", columnDefinition = "TEXT")
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "username", nullable = false, unique = true, length = 60)
    private String username;

    @Column(name = "phone_number", unique = true, length = 30)
    private String phoneNumber;

    @Column(name = "university_name", length = 180)
    private String universityName;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 30)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_status", nullable = false, length = 50)
    private WorkStatus workStatus = WorkStatus.STUDENT;

    @Column(name = "location", length = 120)
    private String location;

    @Column(name = "google_id")
    private String googleId;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 30)
    private AccountStatus accountStatus = AccountStatus.PENDING;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "total_xp", nullable = false)
    private int totalXp = 0;

    @Column(name = "level", nullable = false)
    private int level = 1;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPhone() {
        return phoneNumber;
    }

    public void setPhone(String phone) {
        this.phoneNumber = phone;
    }

    public String getUniversityName() {
        return universityName;
    }

    public void setUniversityName(String universityName) {
        this.universityName = universityName;
    }

    public String getUniversity() {
        return universityName;
    }

    public void setUniversity(String university) {
        this.universityName = university;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public WorkStatus getWorkStatus() {
        return workStatus;
    }

    public void setWorkStatus(WorkStatus workStatus) {
        this.workStatus = workStatus;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
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

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public int getTotalXp() {
        return totalXp;
    }

    public void setTotalXp(int totalXp) {
        this.totalXp = totalXp;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
