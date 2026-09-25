package kh.edu.acleda.campuslink.modules.admin.analytics.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AdminAnalyticsQueryTest {

    @Test
    void defaultsToTheLastThirtyPhnomPenhCalendarDaysAndDailyBuckets() {
        AdminAnalyticsQuery query = AdminAnalyticsQuery.fromRequest(
            null, LocalDate.of(2026, 9, 25), null);

        assertThat(query.from()).isEqualTo(LocalDate.of(2026, 8, 27));
        assertThat(query.to()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(query.granularity()).isEqualTo(AdminAnalyticsQuery.Granularity.DAY);
        assertThat(query.bucketFrom()).isEqualTo(query.from());
        assertThat(query.bucketTo()).isEqualTo(query.to());
    }

    @Test
    void alignsWeekAndMonthBucketsWithoutChangingTheRequestedDateWindow() {
        AdminAnalyticsQuery week = AdminAnalyticsQuery.fromRequest(
            LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 16), "week");
        AdminAnalyticsQuery month = AdminAnalyticsQuery.fromRequest(
            LocalDate.of(2026, 9, 2), LocalDate.of(2026, 10, 16), "month");

        assertThat(week.bucketFrom()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(week.bucketTo()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(month.bucketFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(month.bucketTo()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(month.wireGranularity()).isEqualTo("month");
    }

    @Test
    void rejectsReversedRangesAndUnsupportedGranularity() {
        assertThatThrownBy(() -> AdminAnalyticsQuery.fromRequest(
            LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 25), "day"))
            .hasMessage("from must be on or before to")
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");

        assertThatThrownBy(() -> AdminAnalyticsQuery.fromRequest(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 25), "quarter"))
            .hasMessage("granularity must be one of day, week, or month")
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");
    }
}

