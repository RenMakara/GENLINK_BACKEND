package kh.edu.acleda.campuslink.modules.admin.sanction.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionSeverity;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionType;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

class AdminSanctionListCriteriaTest {

    @Test
    void parsesRepeatedStyleCommaSeparatedFiltersAndPhnomPenhDateRange() {
        UUID adminId = UUID.randomUUID();

        AdminSanctionListCriteria criteria = AdminSanctionListCriteria.fromRequest(
            "warning, BAN",
            "low,HIGH",
            adminId.toString(),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 24),
            true,
            1,
            50);

        assertThat(criteria.types()).containsExactlyInAnyOrder(SanctionType.WARNING, SanctionType.BAN);
        assertThat(criteria.severities())
            .containsExactlyInAnyOrder(SanctionSeverity.LOW, SanctionSeverity.HIGH);
        assertThat(criteria.adminUserId()).isEqualTo(adminId);
        assertThat(criteria.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(criteria.to()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(criteria.activeOnly()).isTrue();
        assertThat(criteria.page()).isEqualTo(1);
        assertThat(criteria.size()).isEqualTo(50);
    }

    @Test
    void rejectsUnknownFiltersInvalidDatesAndUnsupportedPageSizes() {
        assertThatThrownBy(() -> AdminSanctionListCriteria.fromRequest(
            "NOTICE", null, null, null, null, false, 0, 25))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");
        assertThatThrownBy(() -> AdminSanctionListCriteria.fromRequest(
            null, null, "not-a-uuid", null, null, false, 0, 25))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");
        assertThatThrownBy(() -> AdminSanctionListCriteria.fromRequest(
            null, null, null, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 24), false, 0, 25))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");
        assertThatThrownBy(() -> AdminSanctionListCriteria.fromRequest(
            null, null, null, null, null, false, 0, 10))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");
    }
}
