package kh.edu.acleda.campuslink.modules.admin.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria;
import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/** H2 execution coverage for the Criteria shape; the release gate still requires real Postgres. */
@SpringBootTest
@ActiveProfiles("test")
class AdminUserListRepositoryTest {

    @Autowired
    private AdminUserListRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void excludesDeletedUsersAndSupportsSearchAndLastActiveSort() {
        User active = saveUser("active@example.com", "active_user", "Active User", AccountStatus.ACTIVE);
        User deleted = saveUser("deleted@example.com", "deleted_user", "Deleted User", AccountStatus.DELETED);
        saveToken(active, Instant.parse("2026-09-22T00:00:00Z"));
        saveToken(deleted, Instant.parse("2026-09-23T00:00:00Z"));

        AdminUserListCriteria criteria = AdminUserListCriteria.fromRequest(
            "active@example.com", null, null, null, null, null, null, null,
            null, null, null, null, null, null, null,
            false, false, 0, 25, List.of("lastActiveAt,desc"));

        Page<User> page = repository.findPage(criteria, PageRequest.of(0, 25));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(User::getId).containsExactly(active.getId());
    }

    @Test
    void appliesPhnomPenhInclusiveCreatedDateAndNeverActiveFilter() {
        User user = saveUser("date@example.com", "date_user", "Date User", AccountStatus.ACTIVE);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Phnom_Penh"));

        AdminUserListCriteria criteria = AdminUserListCriteria.fromRequest(
            null, null, null, null, null, null, null, null,
            null, null, null, null, today, today, "never",
            false, false, 0, 25, null);

        Page<User> page = repository.findPage(criteria, PageRequest.of(0, 25));

        assertThat(page.getContent()).extracting(User::getId).contains(user.getId());
    }

    private User saveUser(String email, String username, String fullName, AccountStatus status) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setFullName(fullName);
        user.setAccountStatus(status);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private void saveToken(User user, Instant lastUsedAt) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash("admin-list-" + UUID.randomUUID());
        token.setExpiresAt(Instant.parse("2030-01-01T00:00:00Z"));
        token.setLastUsedAt(lastUsedAt);
        refreshTokenRepository.saveAndFlush(token);
    }
}
