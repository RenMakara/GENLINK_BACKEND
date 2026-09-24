package kh.edu.acleda.campuslink.modules.admin.sanction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminSanctionListCriteria;
import kh.edu.acleda.campuslink.modules.admin.sanction.entity.UserSanction;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionSeverity;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionType;
import kh.edu.acleda.campuslink.modules.admin.sanction.repository.UserSanctionRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminSanctionQueryServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSanctionRepository userSanctionRepository;

    private AdminSanctionQueryService service;
    private User user;
    private AdminUser admin;

    @BeforeEach
    void setUp() {
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setFullName("Target User");
        admin = AdminUser.builder().user(user).active(true).build();
        ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
        service = new AdminSanctionQueryService(userRepository, userSanctionRepository);
    }

    @Test
    void mapsInterleavedTimelineAndStablePagination() {
        UserSanction warning = sanction(SanctionType.WARNING, Instant.parse("2026-09-24T04:00:00Z"));
        warning.setSeverity(SanctionSeverity.MEDIUM);
        warning.setMessageEn("Please keep discussions respectful.");
        warning.setMessageKm("សូមរក្សាការពិភាក្សាឱ្យមានការគោរព។");
        PageRequest expectedPage = PageRequest.of(
            0, 25, Sort.by(Sort.Order.desc("startsAt"), Sort.Order.desc("id")));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userSanctionRepository.findAll(
            org.mockito.ArgumentMatchers.<Specification<UserSanction>>any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(warning), expectedPage, 30));

        AdminSanctionListCriteria criteria = AdminSanctionListCriteria.fromRequest(
            "WARNING,SUSPENSION,BAN", null, null, null, null, false, 0, 25);
        PageResponse<kh.edu.acleda.campuslink.modules.admin.sanction.dto.response.AdminSanctionResponse> response =
            service.listForUser(USER_ID, criteria);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(30);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.content().get(0).type()).isEqualTo(SanctionType.WARNING);
        assertThat(response.content().get(0).issuedByAdminId()).isEqualTo(ADMIN_ID);
        assertThat(response.content().get(0).issuedByAdminName()).isEqualTo("Target User");
        assertThat(response.content().get(0).active()).isTrue();
        verify(userSanctionRepository).findAll(
            org.mockito.ArgumentMatchers.<Specification<UserSanction>>any(),
            org.mockito.ArgumentMatchers.eq(expectedPage));
    }

    @Test
    void mapsExpiredAndLiftedRowsAsInactive() {
        UserSanction suspension = sanction(
            SanctionType.SUSPENSION, Instant.parse("2026-09-20T04:00:00Z"));
        suspension.setExpiresAt(Instant.parse("2026-09-21T04:00:00Z"));
        suspension.setLiftedAt(Instant.parse("2026-09-20T06:00:00Z"));
        when(userSanctionRepository.findAll(
            org.mockito.ArgumentMatchers.<Specification<UserSanction>>any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(suspension), PageRequest.of(0, 25), 1));

        PageResponse<kh.edu.acleda.campuslink.modules.admin.sanction.dto.response.AdminSanctionResponse> response =
            service.listGlobal(AdminSanctionListCriteria.fromRequest(
                null, null, null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 24), false, 0, 25));

        assertThat(response.content().get(0).active()).isFalse();
        assertThat(response.content().get(0).liftedAt()).isEqualTo(suspension.getLiftedAt());
    }

    private UserSanction sanction(SanctionType type, Instant startsAt) {
        UserSanction sanction = UserSanction.builder()
            .user(user)
            .adminUser(admin)
            .type(type)
            .reason("Repeated abusive messages")
            .previousStatus(AccountStatus.ACTIVE)
            .startsAt(startsAt)
            .build();
        ReflectionTestUtils.setField(sanction, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(sanction, "createdAt", startsAt);
        return sanction;
    }
}
