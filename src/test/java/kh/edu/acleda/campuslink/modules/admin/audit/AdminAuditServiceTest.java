package kh.edu.acleda.campuslink.modules.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.entity.AdminAuditLog;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import kh.edu.acleda.campuslink.modules.admin.rbac.AdminIdentity;
import kh.edu.acleda.campuslink.modules.admin.rbac.AdminPermissionCache;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.shared.web.RequestId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuditServiceTest {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();

    @Mock
    private AdminAuditWriter writer;
    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private AdminPermissionCache permissionCache;

    @Captor
    private ArgumentCaptor<AdminAuditLog> entry;

    private AdminAuditService service;

    @BeforeEach
    void setUp() {
        service = new AdminAuditService(writer, adminUserRepository, permissionCache, new ObjectMapper());

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(ADMIN_EMAIL, null, java.util.List.of()));
        when(permissionCache.identityFor(ADMIN_EMAIL))
            .thenReturn(new AdminIdentity(ADMIN_ID, UUID.randomUUID(), "ADMIN"));
        when(adminUserRepository.getReferenceById(ADMIN_ID)).thenReturn(new AdminUser());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    /**
     * The bug this guards, already shipped once in {@code SecurityAuditService}: a failing
     * action logs and then throws, rolling its transaction back. If the audit row shared that
     * transaction it would vanish - losing exactly the events the log exists for.
     */
    @Test
    @DisplayName("a FAILURE is written in its own transaction, so it survives the caller's rollback")
    void failureIsWrittenInANewTransaction() {
        service.recordFailure("USER_BAN", "USER", TARGET_ID, "spamming repeatedly", "USER_ALREADY_BANNED");

        verify(writer).writeInNewTransaction(entry.capture());
        verify(writer, never()).writeInCurrentTransaction(any());
        assertThat(entry.getValue().getStatus()).isEqualTo(AuditStatus.FAILURE);
        assertThat(entry.getValue().getErrorCode()).isEqualTo("USER_ALREADY_BANNED");
    }

    /**
     * The mirror-image danger: a SUCCESS row that outlives a rolled-back operation would have
     * the log asserting something that never happened.
     */
    @Test
    @DisplayName("a SUCCESS joins the caller's transaction, so it dies with a rollback")
    void successJoinsTheCallersTransaction() {
        service.recordSuccess("USER_BAN", "USER", TARGET_ID, "spamming repeatedly");

        verify(writer).writeInCurrentTransaction(entry.capture());
        verify(writer, never()).writeInNewTransaction(any());
        assertThat(entry.getValue().getStatus()).isEqualTo(AuditStatus.SUCCESS);
    }

    @Test
    @DisplayName("before/after snapshots are recorded for update-shaped actions")
    void capturesBeforeAndAfterState() {
        service.record(AdminAuditEvent.builder()
            .action("USER_UPDATE").targetType("USER").targetId(TARGET_ID)
            .beforeJson(service.toJson(Map.of("status", "PENDING")))
            .afterJson(service.toJson(Map.of("status", "BANNED")))
            .status(AuditStatus.SUCCESS)
            .build());

        verify(writer).writeInCurrentTransaction(entry.capture());
        assertThat(entry.getValue().getBeforeJson()).contains("PENDING");
        assertThat(entry.getValue().getAfterJson()).contains("BANNED");
    }

    /** AC#3: the client's address, not the reverse proxy's. */
    @Test
    @DisplayName("records the client IP from X-Forwarded-For, not the proxy address")
    void recordsClientIpNotProxyIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");                                   // nginx
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");       // real client first
        request.addHeader("User-Agent", "Mozilla/5.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        service.recordSuccess("USER_BAN", "USER", TARGET_ID, "reason enough");

        verify(writer).writeInCurrentTransaction(entry.capture());
        assertThat(entry.getValue().getIpAddress()).isEqualTo("203.0.113.9");
        assertThat(entry.getValue().getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    @DisplayName("carries the correlation id, tying the row to the request's log lines")
    void recordsCorrelationId() {
        MDC.put(RequestId.MDC_KEY, "req-1234");

        service.recordSuccess("USER_BAN", "USER", TARGET_ID, "reason enough");

        verify(writer).writeInCurrentTransaction(entry.capture());
        assertThat(entry.getValue().getRequestId()).isEqualTo("req-1234");
    }

    /**
     * Bootstrap creates the first SUPER_ADMIN with nobody authenticated, and a pre-auth
     * failure has no actor either. The column is nullable for exactly these cases.
     */
    @Test
    @DisplayName("records an unattributed action when there is no acting admin")
    void tolerAtesNoAuthenticatedAdmin() {
        SecurityContextHolder.clearContext();

        service.recordSuccess("ADMIN_BOOTSTRAP", "ADMIN_USER", TARGET_ID, "first super admin");

        verify(writer).writeInCurrentTransaction(entry.capture());
        assertThat(entry.getValue().getAdminUser()).isNull();
    }

    @Test
    @DisplayName("an authenticated non-admin is not recorded as the actor")
    void nonAdminIsNotRecordedAsActor() {
        when(permissionCache.identityFor(ADMIN_EMAIL)).thenReturn(AdminIdentity.NONE);

        service.recordSuccess("USER_BAN", "USER", TARGET_ID, "reason enough");

        verify(writer).writeInCurrentTransaction(entry.capture());
        assertThat(entry.getValue().getAdminUser()).isNull();
    }

    /**
     * Auditing must never be the reason an admin action fails. Note the catch lives outside
     * the writer's transaction boundary - swallowing it inside would mark that transaction
     * rollback-only and blow up the caller at commit instead.
     */
    @Test
    @DisplayName("a writer failure is swallowed rather than breaking the audited action")
    void writerFailureDoesNotPropagate() {
        doThrow(new RuntimeException("database unavailable"))
            .when(writer).writeInCurrentTransaction(any());

        service.recordSuccess("USER_BAN", "USER", TARGET_ID, "reason enough");
        // No exception escapes.
    }

    @Test
    @DisplayName("toJson returns null rather than throwing on an unserialisable snapshot")
    void toJsonFailsSoft() {
        assertThat(service.toJson(null)).isNull();
        // A snapshot is never worth failing the action for.
        assertThat(service.toJson(new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() {
                throw new IllegalStateException("boom");
            }
        })).isNull();
    }
}
