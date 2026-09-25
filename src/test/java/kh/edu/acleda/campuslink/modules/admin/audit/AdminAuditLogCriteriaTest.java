package kh.edu.acleda.campuslink.modules.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.request.AdminAuditLogCriteria;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import org.junit.jupiter.api.Test;

class AdminAuditLogCriteriaTest {

    @Test
    void normalizesFiltersAndSupportsCaseInsensitiveStatus() {
        UUID adminId = UUID.randomUUID();

        AdminAuditLogCriteria criteria = AdminAuditLogCriteria.fromRequest(
            " " + adminId + " ", " USER_BAN ", " USER ",
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 25), "failure", 2, 50);

        assertThat(criteria.adminUserId()).isEqualTo(adminId);
        assertThat(criteria.action()).isEqualTo("USER_BAN");
        assertThat(criteria.targetType()).isEqualTo("USER");
        assertThat(criteria.status()).isEqualTo(AuditStatus.FAILURE);
        assertThat(criteria.page()).isEqualTo(2);
        assertThat(criteria.size()).isEqualTo(50);
    }

    @Test
    void rejectsInvalidDateRangeAndPageValues() {
        assertThatThrownBy(() -> AdminAuditLogCriteria.fromRequest(
            null, null, null, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 1),
            null, 0, 25))
            .hasMessage("from must be on or before to");

        assertThatThrownBy(() -> AdminAuditLogCriteria.fromRequest(
            null, null, null, null, null, null, -1, 25))
            .hasMessage("page must be zero or greater");

        assertThatThrownBy(() -> AdminAuditLogCriteria.fromRequest(
            null, null, null, null, null, null, 0, 101))
            .hasMessage("size must be between 1 and 100");
    }

    @Test
    void rejectsUnknownStatusAndMalformedAdminId() {
        assertThatThrownBy(() -> AdminAuditLogCriteria.fromRequest(
            "not-a-uuid", null, null, null, null, null, 0, 25))
            .hasMessage("adminId must be a valid UUID");

        assertThatThrownBy(() -> AdminAuditLogCriteria.fromRequest(
            null, null, null, null, null, "maybe", 0, 25))
            .hasMessage("Unsupported status value: maybe");
    }
}
