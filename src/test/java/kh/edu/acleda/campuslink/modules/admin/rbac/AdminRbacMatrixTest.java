package kh.edu.acleda.campuslink.modules.admin.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import kh.edu.acleda.campuslink.modules.admin.badge.AdminBadgeController;
import kh.edu.acleda.campuslink.modules.admin.dashboard.AdminDashboardController;
import kh.edu.acleda.campuslink.modules.admin.notification.AdminNotificationController;
import kh.edu.acleda.campuslink.modules.admin.rbac.AdminRoleReadController;
import kh.edu.acleda.campuslink.modules.admin.rbac.AdminRoleUpdateController;
import kh.edu.acleda.campuslink.modules.admin.sanction.AdminUserSanctionController;
import kh.edu.acleda.campuslink.modules.admin.user.AdminUserDetailController;
import kh.edu.acleda.campuslink.modules.admin.user.AdminUserExportController;
import kh.edu.acleda.campuslink.modules.admin.user.AdminUserLifecycleController;
import kh.edu.acleda.campuslink.modules.admin.user.AdminUserListController;
import kh.edu.acleda.campuslink.modules.admin.user.AdminUserNoteController;
import kh.edu.acleda.campuslink.modules.admin.user.AdminUserRecoveryController;
import kh.edu.acleda.campuslink.modules.admin.user.AdminUserXpAdjustmentController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Executable guardrails for the seven-role matrix in SSOT §5.4.
 *
 * <p>The seed is parsed and compared with a deliberately duplicated expected matrix. This is
 * intentional: changing a role grant without changing this test is a security-review failure,
 * rather than a silently accepted data change. Representative controller methods are checked in
 * the same parameterized suite so the stored grant and the actual method-security contract cannot
 * drift apart.
 */
class AdminRbacMatrixTest {

    private static final String MIGRATION = "db/changelog/changes/0041-create-admin-tables.sql";
    private static final String SEED_CHANGESET = "--changeset genlink:0041-seed-admin-roles";
    private static final Pattern GRANT_PATTERN = Pattern.compile(
        "\\('([A-Z_]+)',\\s*'([a-z_]+(?::[a-z_]+)+)'\\)");

    private static final List<String> ROLE_CODES = List.of(
        "SUPER_ADMIN", "ADMIN", "MODERATOR", "SUPPORT", "CONTENT_MANAGER",
        "FINANCE_ADMIN", "ANALYTICS_VIEWER");

    private static Map<String, Set<String>> expectedMatrix;
    private static Map<String, Set<String>> effectiveSeedMatrix;

    @BeforeAll
    static void loadMatrices() throws IOException {
        expectedMatrix = expectedMatrix();
        Map<String, Set<String>> stored = readStoredMatrix();
        effectiveSeedMatrix = new LinkedHashMap<>(stored);
        // SUPER_ADMIN is deliberately absent from role_permissions and is expanded in code.
        effectiveSeedMatrix.put("SUPER_ADMIN", new LinkedHashSet<>(Permission.allCodes()));
    }

    @Test
    @DisplayName("stored role grants exactly match the reviewed SSOT matrix")
    void storedRoleGrantsMatchReviewedMatrix() {
        Set<String> seededRoles = new LinkedHashSet<>(effectiveSeedMatrix.keySet());
        assertThat(seededRoles).containsExactlyInAnyOrderElementsOf(ROLE_CODES);

        for (String roleCode : ROLE_CODES) {
            assertThat(effectiveSeedMatrix.get(roleCode))
                .as("effective grants for %s", roleCode)
                .containsExactlyInAnyOrderElementsOf(expectedMatrix.get(roleCode));
        }
    }

    @ParameterizedTest(name = "{0} × {1}")
    @MethodSource("rolePermissionCases")
    @DisplayName("every role × permission cell is asserted")
    void everyRolePermissionCellMatchesTheReviewedMatrix(String roleCode, String permissionCode) {
        boolean expected = expectedMatrix.get(roleCode).contains(permissionCode);
        boolean actual = effectiveSeedMatrix.get(roleCode).contains(permissionCode);
        assertThat(actual)
            .as("whether %s grants %s", roleCode, permissionCode)
            .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("roleEndpointCases")
    @DisplayName("representative endpoints use the permission matrix contract")
    void representativeEndpointMatchesRoleMatrix(String roleCode, EndpointContract endpoint)
        throws NoSuchMethodException {
        Method method = findMethod(endpoint.controller(), endpoint.methodName());
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);

        assertThat(authorization)
            .as("@PreAuthorize on %s", endpoint.label())
            .isNotNull();
        assertThat(authorization.value())
            .as("permission contract for %s", endpoint.label())
            .isEqualTo(endpoint.expression());
        assertThat(endpoint.isAllowed(expectedMatrix.get(roleCode)))
            .as("whether %s may call %s", roleCode, endpoint.label())
            .isEqualTo(endpoint.isAllowed(effectiveSeedMatrix.get(roleCode)));
    }

    @ParameterizedTest(name = "ANALYTICS_VIEWER → {0}")
    @MethodSource("writeEndpointCases")
    @DisplayName("ANALYTICS_VIEWER has no representative write access")
    void analyticsViewerCannotUseWriteEndpoints(EndpointContract endpoint)
        throws NoSuchMethodException {
        Method method = findMethod(endpoint.controller(), endpoint.methodName());
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(endpoint.isAllowed(expectedMatrix.get("ANALYTICS_VIEWER")))
            .as("ANALYTICS_VIEWER access to %s", endpoint.label())
            .isFalse();
    }

    @Test
    @DisplayName("SUPPORT can reset passwords but cannot ban")
    void supportRecoveryWithoutEnforcement() {
        assertThat(has("SUPPORT", "user:reset_password")).isTrue();
        assertThat(has("SUPPORT", "user:ban")).isFalse();
    }

    @Test
    @DisplayName("MODERATOR can suspend but cannot delete")
    void moderatorEnforcementWithoutLifecycleDelete() {
        assertThat(has("MODERATOR", "user:suspend")).isTrue();
        assertThat(has("MODERATOR", "user:delete")).isFalse();
    }

    private static boolean has(String roleCode, String permissionCode) {
        return expectedMatrix.get(roleCode).contains(permissionCode);
    }

    private static Stream<Arguments> rolePermissionCases() {
        return ROLE_CODES.stream()
            .flatMap(role -> Permission.allCodes().stream()
                .map(permission -> Arguments.of(role, permission)));
    }

    private static Stream<Arguments> roleEndpointCases() {
        return representativeEndpoints().stream()
            .flatMap(endpoint -> ROLE_CODES.stream()
                .map(role -> Arguments.of(role, endpoint)));
    }

    private static Stream<Arguments> writeEndpointCases() {
        return representativeEndpoints().stream()
            .filter(EndpointContract::write)
            .map(Arguments::of);
    }

    /**
     * These are the implemented permission boundaries. Planned groups such as finance,
     * analytics and audit are still checked at the matrix level until their endpoints exist.
     */
    private static List<EndpointContract> representativeEndpoints() {
        return List.of(
            EndpointContract.read("dashboard summary", AdminDashboardController.class, "summary",
                "dashboard:view"),
            EndpointContract.read("user directory", AdminUserListController.class, "list",
                "user:read"),
            EndpointContract.read("user full detail boundary", AdminUserDetailController.class, "get",
                "user:read"),
            EndpointContract.write("verify email", AdminUserRecoveryController.class, "verifyEmail",
                "user:verify_email"),
            EndpointContract.write("reset password", AdminUserRecoveryController.class, "resetPassword",
                "user:reset_password"),
            EndpointContract.write("force logout", AdminUserRecoveryController.class, "forceLogout",
                "user:force_logout"),
            EndpointContract.write("ban user", AdminUserSanctionController.class, "ban", "user:ban"),
            EndpointContract.write("unban user", AdminUserSanctionController.class, "unban", "user:unban"),
            EndpointContract.write("suspend user", AdminUserSanctionController.class, "suspend",
                "user:suspend"),
            EndpointContract.write("delete user", AdminUserLifecycleController.class, "delete",
                "user:delete"),
            EndpointContract.write("write user note", AdminUserNoteController.class, "create",
                "user:note:write"),
            EndpointContract.write("adjust XP", AdminUserXpAdjustmentController.class, "adjust",
                "xp:grant", "xp:revoke"),
            EndpointContract.write("assign badge", AdminBadgeController.class, "list", "badge:assign"),
            EndpointContract.write("broadcast notification", AdminNotificationController.class, "broadcast",
                "notification:send"),
            EndpointContract.write("manage notification templates", AdminNotificationController.class,
                "listTemplates", "notification:template:manage"),
            EndpointContract.write("export users", AdminUserExportController.class, "export", "user:export"),
            EndpointContract.read("read roles", AdminRoleReadController.class, "listRoles", "rbac:read"),
            EndpointContract.write("manage roles", AdminRoleUpdateController.class, "replacePermissions",
                "rbac:manage"));
    }

    private static Method findMethod(Class<?> controller, String methodName)
        throws NoSuchMethodException {
        return Arrays.stream(controller.getDeclaredMethods())
            .filter(method -> method.getName().equals(methodName))
            .findFirst()
            .orElseThrow(() -> new NoSuchMethodException(
                controller.getSimpleName() + "#" + methodName));
    }

    private record EndpointContract(
        String label,
        Class<?> controller,
        String methodName,
        List<String> permissions,
        boolean write
    ) {
        static EndpointContract read(
            String label, Class<?> controller, String methodName, String... permissions) {
            return new EndpointContract(label, controller, methodName, List.of(permissions), false);
        }

        static EndpointContract write(
            String label, Class<?> controller, String methodName, String... permissions) {
            return new EndpointContract(label, controller, methodName, List.of(permissions), true);
        }

        String expression() {
            if (permissions.size() == 1) {
                return "hasAuthority('PERM_" + permissions.get(0) + "')";
            }
            return "hasAnyAuthority(" + permissions.stream()
                .map(permission -> "'PERM_" + permission + "'")
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow() + ")";
        }

        boolean isAllowed(Set<String> grants) {
            return permissions.stream().anyMatch(grants::contains);
        }
    }

    private static Map<String, Set<String>> expectedMatrix() {
        Map<String, Set<String>> matrix = new LinkedHashMap<>();
        matrix.put("SUPER_ADMIN", new LinkedHashSet<>(Permission.allCodes()));
        matrix.put("ADMIN", Set.of(
            "dashboard:view", "user:read", "user:update", "user:verify_email",
            "user:reset_password", "user:force_logout", "user:warn", "user:note:write",
            "user:suspend", "user:ban", "user:unban", "user:delete", "user:export",
            "xp:grant", "xp:revoke", "badge:assign", "gamification:manage",
            "community:moderate", "forum:moderate", "story:moderate", "event:read",
            "event:manage", "opportunity:manage", "media:read", "media:delete",
            "notification:send", "notification:template:manage", "report:read", "report:resolve",
            "finance:read", "finance:ledger:read", "analytics:view", "analytics:export",
            "localization:manage", "rbac:read", "audit:read", "settings:read"));
        matrix.put("MODERATOR", Set.of(
            "dashboard:view", "user:read", "user:force_logout", "user:warn", "user:note:write",
            "user:suspend", "user:ban", "user:unban", "community:moderate", "forum:moderate",
            "story:moderate", "event:read", "media:read", "media:delete", "report:read",
            "report:resolve", "analytics:view", "audit:read"));
        matrix.put("SUPPORT", Set.of(
            "dashboard:view", "user:read", "user:verify_email", "user:reset_password",
            "user:force_logout", "user:warn", "user:note:write", "event:read", "report:read"));
        matrix.put("CONTENT_MANAGER", Set.of(
            "dashboard:view", "badge:assign", "gamification:manage", "story:moderate", "event:read",
            "event:manage", "opportunity:manage", "media:read", "media:delete", "notification:send",
            "notification:template:manage", "analytics:view", "localization:manage"));
        matrix.put("FINANCE_ADMIN", Set.of(
            "dashboard:view", "user:read", "finance:read", "finance:ledger:read", "analytics:view",
            "analytics:export", "audit:read"));
        matrix.put("ANALYTICS_VIEWER", Set.of(
            "dashboard:view", "event:read", "finance:read", "analytics:view", "analytics:export"));
        return matrix;
    }

    private static Map<String, Set<String>> readStoredMatrix() throws IOException {
        String sql;
        try (InputStream input = AdminRbacMatrixTest.class.getClassLoader()
            .getResourceAsStream(MIGRATION)) {
            if (input == null) {
                throw new IOException("Missing migration resource " + MIGRATION);
            }
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        int seedStart = sql.indexOf(SEED_CHANGESET);
        if (seedStart < 0) {
            throw new IOException("Missing seed changeset " + SEED_CHANGESET);
        }

        Matcher matcher = GRANT_PATTERN.matcher(sql.substring(seedStart));
        Map<String, Set<String>> matrix = new LinkedHashMap<>();
        while (matcher.find()) {
            matrix.computeIfAbsent(matcher.group(1), ignored -> new LinkedHashSet<>())
                .add(matcher.group(2));
        }

        if (matrix.isEmpty()) {
            throw new IOException("No role grants found in " + MIGRATION);
        }
        return matrix;
    }
}
