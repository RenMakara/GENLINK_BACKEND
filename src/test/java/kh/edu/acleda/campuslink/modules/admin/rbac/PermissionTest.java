package kh.edu.acleda.campuslink.modules.admin.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Guards the seam left open by having no {@code permissions} table. */
class PermissionTest {

    private static final String MIGRATION = "db/changelog/changes/0041-create-admin-tables.sql";

    /** Pins the SSOT §5.3 key list. Changing it is a deliberate act, not a drive-by edit. */
    private static final int EXPECTED_PERMISSION_COUNT = 40;

    @Test
    @DisplayName("catalogue matches the SSOT key count")
    void catalogueSize() {
        assertThat(Permission.values()).hasSize(EXPECTED_PERMISSION_COUNT);
    }

    @Test
    @DisplayName("codes are unique")
    void codesAreUnique() {
        // A duplicate would silently grant one permission when the other was intended.
        Set<String> codes = new TreeSet<>(Permission.allCodes());
        assertThat(codes).hasSize(Permission.values().length);
    }

    @Test
    @DisplayName("codes are lower-case, colon-separated resource:action")
    void codeFormat() {
        Pattern shape = Pattern.compile("[a-z_]+(:[a-z_]+)+");
        for (Permission permission : Permission.values()) {
            assertThat(permission.getCode())
                .as("code of %s", permission.name())
                .matches(shape);
        }
    }

    @Test
    @DisplayName("authorities are PERM_-prefixed so they cannot collide with ROLE_")
    void authorityPrefix() {
        assertThat(Permission.USER_BAN.asAuthority().getAuthority()).isEqualTo("PERM_user:ban");
        for (Permission permission : Permission.values()) {
            assertThat(permission.asAuthority().getAuthority()).startsWith("PERM_");
        }
    }

    @Test
    @DisplayName("resource and action split correctly, including three-segment codes")
    void resourceAndAction() {
        assertThat(Permission.USER_BAN.getResource()).isEqualTo("user");
        assertThat(Permission.USER_BAN.getAction()).isEqualTo("ban");
        // Not every code is two segments; these qualify the action further.
        assertThat(Permission.USER_EXPORT_PII.getResource()).isEqualTo("user");
        assertThat(Permission.USER_EXPORT_PII.getAction()).isEqualTo("export:pii");
        assertThat(Permission.NOTIFICATION_TEMPLATE_MANAGE.getResource()).isEqualTo("notification");
        assertThat(Permission.NOTIFICATION_TEMPLATE_MANAGE.getAction()).isEqualTo("template:manage");
    }

    @Test
    @DisplayName("fromCode resolves known codes and returns empty for unknown ones")
    void fromCode() {
        assertThat(Permission.fromCode("user:ban")).contains(Permission.USER_BAN);
        // Empty rather than throwing: one stale role_permissions row must not be able to
        // lock every admin out of the system.
        assertThat(Permission.fromCode("user:obliterate")).isEmpty();
        assertThat(Permission.fromCode(null)).isEmpty();
    }

    /** Every seeded permission code must resolve. */
    @Test
    @DisplayName("every permission_code seeded in migration 0041 resolves to a constant")
    void seededCodesAllResolve() throws IOException {
        List<String> seeded = seededPermissionCodes();

        assertThat(seeded)
            .as("no grants found - the migration or the parser has changed shape")
            .isNotEmpty();

        List<String> unresolved = seeded.stream()
            .distinct()
            .filter(code -> Permission.fromCode(code).isEmpty())
            .collect(Collectors.toList());

        assertThat(unresolved)
            .as("seeded in 0041 but missing from the Permission enum")
            .isEmpty();
    }

    @Test
    @DisplayName("the seed grants 89 permissions, matching the SSOT matrix")
    void seededGrantCount() throws IOException {
        // Cross-checks the migration against SSOT §5.4.
        assertThat(seededPermissionCodes()).hasSize(89);
    }

    /** Extracts the {@code ('ROLE', 'permission:code')} pairs from the seed changeset. */
    private List<String> seededPermissionCodes() throws IOException {
        String sql;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            assertThat(in).as("migration %s not on the classpath", MIGRATION).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Only the seed changeset; the DDL above it contains no grant tuples.
        int seedStart = sql.indexOf("--changeset genlink:0041-seed-admin-roles");
        assertThat(seedStart).as("seed changeset not found in the migration").isNotNegative();
        String seedBlock = sql.substring(seedStart);

        Matcher matcher = Pattern
            .compile("\\('([A-Z_]+)',\\s*'([a-z_]+(?::[a-z_]+)+)'\\)")
            .matcher(seedBlock);

        List<String> codes = new ArrayList<>();
        while (matcher.find()) {
            codes.add(matcher.group(2));
        }
        return codes;
    }
}
