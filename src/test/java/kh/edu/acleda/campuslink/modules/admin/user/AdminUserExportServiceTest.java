package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria;
import kh.edu.acleda.campuslink.modules.admin.user.repository.AdminUserListRepository;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.Gender;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminUserExportServiceTest {

    private static final String REASON = "Support export request";

    @Mock private AdminUserListRepository listRepository;
    @Mock private AdminUserRepository adminUserRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AdminAuditService auditService;

    private AdminUserExportService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserExportService(
            listRepository, adminUserRepository, refreshTokenRepository, auditService);
        when(auditService.toJson(any())).thenReturn("{}");
        lenient().when(adminUserRepository.findByUserIdIn(anyCollection())).thenReturn(List.of());
        lenient().when(refreshTokenRepository.findLastActiveByUserIdIn(anyCollection()))
            .thenReturn(List.of());
    }

    @Test
    void masksPiiAndEscapesCsvValuesWithoutExportingForbiddenFields() throws Exception {
        User user = user("Doe, \"Jane\"");
        AdminUserListCriteria criteria = criteria();
        when(listRepository.count(criteria)).thenReturn(1L);
        when(listRepository.findBatch(criteria, 0, AdminUserExportService.EXPORT_BATCH_SIZE))
            .thenReturn(List.of(user));

        AdminUserExportPlan plan = service.prepare(criteria, false, REASON);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeCsv(plan, output);
        String csv = output.toString(StandardCharsets.UTF_8);

        assertThat(csv).contains("userId,fullName,username,email");
        assertThat(csv).contains("phoneNumber,dateOfBirth");
        assertThat(csv).contains("\"Doe, \"\"Jane\"\"\"");
        assertThat(csv).contains("[REDACTED],[REDACTED]");
        assertThat(csv).doesNotContain("password_hash", "google_id", "token_version", "hash");
    }

    @Test
    void includesPiiOnlyWhenTheSeparateExportPermissionWasResolved() throws Exception {
        User user = user("Private User");
        AdminUserListCriteria criteria = criteria();
        when(listRepository.count(criteria)).thenReturn(1L);
        when(listRepository.findBatch(criteria, 0, AdminUserExportService.EXPORT_BATCH_SIZE))
            .thenReturn(List.of(user));

        AdminUserExportPlan plan = service.prepare(criteria, true, REASON);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeCsv(plan, output);
        String csv = output.toString(StandardCharsets.UTF_8);

        assertThat(csv).contains("+855123456", "1999-04-05");
        assertThat(plan.piiIncluded()).isTrue();
    }

    @Test
    void rejectsOversizedExportsBeforeOpeningTheCsvStream() {
        AdminUserListCriteria criteria = criteria();
        when(listRepository.count(criteria))
            .thenReturn((long) AdminUserExportService.MAX_EXPORT_ROWS + 1);

        assertThatThrownBy(() -> service.prepare(criteria, false, REASON))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.EXPORT_TOO_LARGE);

        verify(listRepository).count(criteria);
        ArgumentCaptor<AdminAuditEvent> audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("USER_EXPORT");
        assertThat(audit.getValue().status()).isEqualTo(AuditStatus.FAILURE);
        assertThat(audit.getValue().errorCode()).isEqualTo(AdminErrorCode.EXPORT_TOO_LARGE);
    }

    @Test
    void auditsSuccessfulExportWithFilterCountAndPiiMode() {
        AdminUserListCriteria criteria = criteria();
        when(listRepository.count(criteria)).thenReturn(12L);

        service.prepare(criteria, false, REASON);

        ArgumentCaptor<AdminAuditEvent> audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().targetType()).isEqualTo("USER_EXPORT");
        assertThat(audit.getValue().reason()).isEqualTo(REASON);
        assertThat(audit.getValue().status()).isEqualTo(AuditStatus.SUCCESS);
        assertThat(audit.getValue().afterJson()).isEqualTo("{}");
    }

    private AdminUserListCriteria criteria() {
        return AdminUserListCriteria.fromRequest(
            "example.com", "ACTIVE", true, "STUDENT", "ACME",
            "MALE", "ENGLISH", "LOCAL", 1, 10, 0, 500,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 25), "7d",
            false, false, 0, 25, List.of("createdAt,desc"));
    }

    private User user(String fullName) {
        UUID id = UUID.randomUUID();
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-09-01T00:00:00Z"));
        user.setFullName(fullName);
        user.setUsername("private_user");
        user.setEmail("private@example.com");
        user.setPhoneNumber("+855123456");
        user.setDateOfBirth(LocalDate.of(1999, 4, 5));
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setGender(Gender.MALE);
        ReflectionTestUtils.setField(user, "authProvider", AuthProvider.LOCAL);
        return user;
    }
}
