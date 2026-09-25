package kh.edu.acleda.campuslink.modules.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.request.AdminAuditLogCriteria;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.response.AdminAuditLogDetailResponse;
import kh.edu.acleda.campuslink.modules.admin.audit.entity.AdminAuditLog;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import kh.edu.acleda.campuslink.modules.admin.audit.repository.AdminAuditLogRepository;
import kh.edu.acleda.campuslink.modules.admin.rbac.PermissionScopeResolver;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;

class AdminAuditLogQueryServiceTest {

    private static final UUID AUDIT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Test
    void detailReturnsStructuredBeforeAndAfterSnapshots() {
        AdminAuditLogRepository repository = mock(AdminAuditLogRepository.class);
        PermissionScopeResolver resolver = mock(PermissionScopeResolver.class);
        AdminAuditLog log = mockLog(ACTOR_ID);
        when(resolver.resolveAuditLogScope()).thenReturn(
            PermissionScopeResolver.AuditLogScope.all(UUID.randomUUID(), "ADMIN"));
        when(repository.findById(AUDIT_ID)).thenReturn(Optional.of(log));

        AdminAuditLogDetailResponse response = new AdminAuditLogQueryService(
            repository, resolver, new ObjectMapper()).detail(AUDIT_ID);

        assertThat(response.beforeJson().get("status").asText()).isEqualTo("PENDING");
        assertThat(response.afterJson().get("status").asText()).isEqualTo("BANNED");
        assertThat(response.adminUserId()).isEqualTo(ACTOR_ID);
        assertThat(response.adminName()).isEqualTo("Admin Example");
        assertThat(response.status()).isEqualTo(AuditStatus.SUCCESS);
    }

    @Test
    void ownScopeCannotReadAnotherAdminsDetail() {
        AdminAuditLogRepository repository = mock(AdminAuditLogRepository.class);
        PermissionScopeResolver resolver = mock(PermissionScopeResolver.class);
        when(resolver.resolveAuditLogScope()).thenReturn(
            PermissionScopeResolver.AuditLogScope.own(ACTOR_ID, "MODERATOR"));
        AdminAuditLog otherAdminLog = mockLog(UUID.randomUUID());
        when(repository.findById(AUDIT_ID)).thenReturn(Optional.of(otherAdminLog));

        AdminAuditLogQueryService service = new AdminAuditLogQueryService(
            repository, resolver, new ObjectMapper());

        assertThatThrownBy(() -> service.detail(AUDIT_ID))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Audit log not found");
    }

    @Test
    void criteriaListIsMappedToStablePageResponse() {
        AdminAuditLogRepository repository = mock(AdminAuditLogRepository.class);
        PermissionScopeResolver resolver = mock(PermissionScopeResolver.class);
        AdminAuditLog log = mockLog(ACTOR_ID);
        when(resolver.resolveAuditLogScope()).thenReturn(
            PermissionScopeResolver.AuditLogScope.all(UUID.randomUUID(), "ADMIN"));
        when(repository.findAll(
            org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<AdminAuditLog>>any(),
            org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(log)));

        AdminAuditLogCriteria criteria = AdminAuditLogCriteria.fromRequest(
            null, "USER_BAN", null, null, null, "SUCCESS", 0, 25);
        var response = new AdminAuditLogQueryService(repository, resolver, new ObjectMapper())
            .list(criteria);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).action()).isEqualTo("USER_BAN");
        assertThat(response.page()).isZero();
    }

    private static AdminAuditLog mockLog(UUID actorId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getFullName()).thenReturn("Admin Example");
        when(user.getEmail()).thenReturn("admin@example.com");
        AdminUser admin = mock(AdminUser.class);
        when(admin.getId()).thenReturn(actorId);
        when(admin.getUser()).thenReturn(user);

        AdminAuditLog log = mock(AdminAuditLog.class);
        when(log.getId()).thenReturn(AUDIT_ID);
        when(log.getCreatedAt()).thenReturn(Instant.parse("2026-09-26T00:00:00Z"));
        when(log.getAdminUser()).thenReturn(admin);
        when(log.getAction()).thenReturn("USER_BAN");
        when(log.getTargetType()).thenReturn("USER");
        when(log.getTargetId()).thenReturn(UUID.randomUUID());
        when(log.getBeforeJson()).thenReturn("{\"status\":\"PENDING\"}");
        when(log.getAfterJson()).thenReturn("{\"status\":\"BANNED\"}");
        when(log.getReason()).thenReturn("Policy violation");
        when(log.getStatus()).thenReturn(AuditStatus.SUCCESS);
        return log;
    }
}
