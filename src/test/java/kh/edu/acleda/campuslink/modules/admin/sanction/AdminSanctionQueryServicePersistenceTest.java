package kh.edu.acleda.campuslink.modules.admin.sanction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminRoleRepository;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminSanctionListCriteria;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.response.AdminSanctionResponse;
import kh.edu.acleda.campuslink.modules.admin.sanction.entity.UserSanction;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionSeverity;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionType;
import kh.edu.acleda.campuslink.modules.admin.sanction.repository.UserSanctionRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** H2 execution coverage for the ADM-084 Specification and entity-graph query. */
@SpringBootTest
@ActiveProfiles("test")
class AdminSanctionQueryServicePersistenceTest {

    private static final Instant WARNING_START = Instant.parse("2026-09-24T02:00:00Z");
    private static final Instant EXPIRED_START = Instant.parse("2026-09-20T02:00:00Z");

    @Autowired
    private AdminSanctionQueryService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AdminRoleRepository adminRoleRepository;
    @Autowired
    private AdminUserRepository adminUserRepository;
    @Autowired
    private UserSanctionRepository userSanctionRepository;

    @AfterEach
    void cleanUp() {
        userSanctionRepository.deleteAllInBatch();
        adminUserRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        adminRoleRepository.deleteAllInBatch();
    }

    @Test
    void executesPerUserAndGlobalFiltersAgainstTheMappedRepository() {
        User target = saveUser("adm084-target@example.com", "adm084_target", "ADM-084 Target");
        User other = saveUser("adm084-other@example.com", "adm084_other", "ADM-084 Other");
        AdminRole role = adminRoleRepository.saveAndFlush(AdminRole.builder()
            .code("ADM084_TEST")
            .name("ADM-084 Test")
            .isSystem(false)
            .build());
        AdminUser admin = adminUserRepository.saveAndFlush(AdminUser.builder()
            .user(other)
            .adminRole(role)
            .active(true)
            .build());

        saveSanction(UserSanction.builder()
            .user(target)
            .adminUser(admin)
            .type(SanctionType.WARNING)
            .severity(SanctionSeverity.HIGH)
            .reason("Repeated abusive messages")
            .messageEn("Please keep discussions respectful.")
            .messageKm("សូមរក្សាការពិភាក្សាឱ្យមានការគោរព។")
            .previousStatus(AccountStatus.ACTIVE)
            .startsAt(WARNING_START)
            .build());
        saveSanction(UserSanction.builder()
            .user(target)
            .adminUser(admin)
            .type(SanctionType.SUSPENSION)
            .reason("Temporary restriction for review")
            .previousStatus(AccountStatus.ACTIVE)
            .startsAt(EXPIRED_START)
            .expiresAt(EXPIRED_START.plusSeconds(3600))
            .build());
        UserSanction deleted = UserSanction.builder()
            .user(other)
            .adminUser(admin)
            .type(SanctionType.BAN)
            .reason("Deleted fixture")
            .previousStatus(AccountStatus.ACTIVE)
            .startsAt(WARNING_START)
            .deletedAt(Instant.parse("2026-09-24T03:00:00Z"))
            .build();
        saveSanction(deleted);

        PageResponse<AdminSanctionResponse> timeline = service.listForUser(
            target.getId(),
            AdminSanctionListCriteria.fromRequest(
                null, null, null, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 24),
                false, 0, 25));
        assertThat(timeline.totalElements()).isEqualTo(2);
        assertThat(timeline.content()).extracting(AdminSanctionResponse::type)
            .containsExactly(SanctionType.WARNING, SanctionType.SUSPENSION);
        assertThat(timeline.content().get(0).issuedByAdminName()).isEqualTo("ADM-084 Other");
        assertThat(timeline.content().get(0).messageKm())
            .isEqualTo("សូមរក្សាការពិភាក្សាឱ្យមានការគោរព។");

        PageResponse<AdminSanctionResponse> activeGlobal = service.listGlobal(
            AdminSanctionListCriteria.fromRequest(
                null, "HIGH", admin.getId().toString(),
                LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 24),
                true, 0, 25));
        assertThat(activeGlobal.totalElements()).isEqualTo(1);
        assertThat(activeGlobal.content()).extracting(AdminSanctionResponse::type)
            .containsExactly(SanctionType.WARNING);
        assertThat(activeGlobal.content().get(0).active()).isTrue();
    }

    private User saveUser(String email, String username, String fullName) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setFullName(fullName);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private UserSanction saveSanction(UserSanction sanction) {
        return userSanctionRepository.saveAndFlush(sanction);
    }
}
