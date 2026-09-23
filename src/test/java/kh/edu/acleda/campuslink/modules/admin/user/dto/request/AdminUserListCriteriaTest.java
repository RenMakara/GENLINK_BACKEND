package kh.edu.acleda.campuslink.modules.admin.user.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.Gender;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

class AdminUserListCriteriaTest {

    @Test
    void parsesAllDirectoryFiltersAndWhitelistedSorts() {
        AdminUserListCriteria criteria = AdminUserListCriteria.fromRequest(
            "  support@example.com ",
            "ACTIVE,PENDING",
            true,
            "STUDENT,FREELANCER",
            "ACME University",
            "MALE,OTHER",
            "ENGLISH,KHMER",
            "LOCAL,GOOGLE",
            2,
            10,
            100,
            5000,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 9, 23),
            "7d",
            true,
            false,
            1,
            50,
            List.of("lastActiveAt,desc", "fullName,asc"));

        assertThat(criteria.search()).isEqualTo("support@example.com");
        assertThat(criteria.statuses()).containsExactlyInAnyOrder(AccountStatus.ACTIVE, AccountStatus.PENDING);
        assertThat(criteria.emailVerified()).isTrue();
        assertThat(criteria.workStatuses()).containsExactlyInAnyOrder(WorkStatus.STUDENT, WorkStatus.FREELANCER);
        assertThat(criteria.genders()).containsExactlyInAnyOrder(Gender.MALE, Gender.OTHER);
        assertThat(criteria.languages()).containsExactlyInAnyOrder(Language.ENGLISH, Language.KHMER);
        assertThat(criteria.authProviders()).containsExactlyInAnyOrder(AuthProvider.LOCAL, AuthProvider.GOOGLE);
        assertThat(criteria.lastActive()).isEqualTo(AdminUserListCriteria.LastActivePreset.WITHIN_7_DAYS);
        assertThat(criteria.banned()).isTrue();
        assertThat(criteria.includeDeleted()).isFalse();
        assertThat(criteria.sortOrders()).extracting(AdminUserListCriteria.SortOrder::field)
            .containsExactly(
                AdminUserListCriteria.SortField.LAST_ACTIVE_AT,
                AdminUserListCriteria.SortField.FULL_NAME);
        assertThat(criteria.sortOrders().get(0).direction()).isEqualTo(AdminUserListCriteria.Direction.DESC);
    }

    @Test
    void appliesSafeDefaults() {
        AdminUserListCriteria criteria = AdminUserListCriteria.fromRequest(
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null,
            false, false, 0, 25, null);

        assertThat(criteria.sortOrders()).containsExactly(
            new AdminUserListCriteria.SortOrder(
                AdminUserListCriteria.SortField.CREATED_AT,
                AdminUserListCriteria.Direction.DESC));
        assertThat(criteria.includeDeleted()).isFalse();
        assertThat(criteria.statuses()).isEmpty();
    }

    @Test
    void rejectsUnrecognisedEnumsSortFieldsAndInvalidRanges() {
        assertValidation(() -> AdminUserListCriteria.fromRequest(
            null, "ACTIVE,NOT_A_STATUS", null, null, null, null, null, null,
            null, null, null, null, null, null, null, false, false, 0, 25, null));
        assertValidation(() -> AdminUserListCriteria.fromRequest(
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, false, false, 0, 25,
            List.of("passwordHash,asc")));
        assertValidation(() -> AdminUserListCriteria.fromRequest(
            null, null, null, null, null, null, null, null,
            10, 2, null, null, null, null, null, false, false, 0, 25, null));
        assertValidation(() -> AdminUserListCriteria.fromRequest(
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, "yesterday", false, false, 0, 25, null));
    }

    @Test
    void detectsExactUuidSearchWithoutChangingTextSearch() {
        AdminUserListCriteria uuidCriteria = AdminUserListCriteria.fromRequest(
            "550e8400-e29b-41d4-a716-446655440000", null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, false, false, 0, 25, null);
        AdminUserListCriteria textCriteria = AdminUserListCriteria.fromRequest(
            "makara", null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, false, false, 0, 25, null);

        assertThat(uuidCriteria.searchUuid()).isNotNull();
        assertThat(textCriteria.searchUuid()).isNull();
    }

    private static void assertValidation(Runnable action) {
        assertThatThrownBy(() -> action.run())
            .isInstanceOf(ApiException.class)
            .extracting(exception -> ((ApiException) exception).getErrorCode())
            .isEqualTo("VALIDATION_ERROR");
    }
}
