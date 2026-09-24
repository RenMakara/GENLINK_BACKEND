package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.rbac.Permission;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserXpAdjustmentRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserXpAdjustmentResponse;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AdminUserXpAdjustmentServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;
    @Mock
    private RewardService rewardService;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminUserXpAdjustmentService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserXpAdjustmentService(userRepository, rewardService, adminAuditService);
        lenient().when(adminAuditService.toJson(any())).thenReturn("{}");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void grantUsesRewardPipelineAndAuditsTheResult() {
        authenticate(Permission.XP_GRANT);
        User user = user(200, 3);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(rewardService.award(eq(user), eq("ADMIN_ADJUSTMENT"), isNull(UUID.class), eq(200)))
            .thenAnswer(invocation -> {
                user.setTotalXp(400);
                user.setLevel(5);
                return 200;
            });

        AdminUserXpAdjustmentResponse response = service.adjust(
            USER_ID, new AdminUserXpAdjustmentRequest(200, "Support correction after review"));

        assertThat(response.requestedAmount()).isEqualTo(200);
        assertThat(response.appliedAmount()).isEqualTo(200);
        assertThat(response.totalXp()).isEqualTo(400);
        assertThat(response.level()).isEqualTo(5);
        verify(rewardService).award(eq(user), eq("ADMIN_ADJUSTMENT"), isNull(UUID.class), eq(200));

        ArgumentCaptor<AdminAuditEvent> audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(adminAuditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("USER_XP_GRANT");
        assertThat(audit.getValue().targetId()).isEqualTo(USER_ID);
        assertThat(audit.getValue().reason()).isEqualTo("Support correction after review");
    }

    @Test
    void revokeUsesSignedDeltaAndReturnsTheFlooredAppliedAmount() {
        authenticate(Permission.XP_REVOKE);
        User user = user(50, 1);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(rewardService.award(eq(user), eq("ADMIN_ADJUSTMENT"), isNull(UUID.class), eq(-200)))
            .thenAnswer(invocation -> {
                user.setTotalXp(0);
                user.setLevel(1);
                return -50;
            });

        AdminUserXpAdjustmentResponse response = service.adjust(
            USER_ID, new AdminUserXpAdjustmentRequest(-200, "Remove invalid reward points"));

        assertThat(response.appliedAmount()).isEqualTo(-50);
        assertThat(response.totalXp()).isZero();
        assertThat(response.level()).isEqualTo(1);
        verify(rewardService).award(eq(user), eq("ADMIN_ADJUSTMENT"), isNull(UUID.class), eq(-200));

        ArgumentCaptor<AdminAuditEvent> audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(adminAuditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("USER_XP_REVOKE");
    }

    @Test
    void grantOnlyAdminCannotSendNegativeAmount() {
        authenticate(Permission.XP_GRANT);

        assertThatThrownBy(() -> service.adjust(
            USER_ID, new AdminUserXpAdjustmentRequest(-25, "Remove invalid reward points")))
            .isInstanceOf(ForbiddenException.class)
            .hasFieldOrPropertyWithValue("errorCode", "FORBIDDEN");

        verifyNoInteractions(userRepository, rewardService, adminAuditService);
    }

    @Test
    void zeroAmountIsRejectedBeforeTheRewardPipeline() {
        authenticate(Permission.XP_GRANT);

        assertThatThrownBy(() -> service.adjust(
            USER_ID, new AdminUserXpAdjustmentRequest(0, "Correct an invalid adjustment")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");

        verifyNoInteractions(userRepository, rewardService, adminAuditService);
    }

    private void authenticate(Permission permission) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(permission.asAuthority())));
    }

    private User user(int totalXp, int level) {
        User user = new User();
        user.setTotalXp(totalXp);
        user.setLevel(level);
        return user;
    }
}
