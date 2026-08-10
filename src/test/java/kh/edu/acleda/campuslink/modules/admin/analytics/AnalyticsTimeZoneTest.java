package kh.edu.acleda.campuslink.modules.admin.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pins the day-boundary cases from SSOT §9.5.
 *
 * <p>Worth a dedicated test because the failure mode is invisible: a wrong conversion shifts
 * every chart by seven hours while still producing plausible-looking numbers. Nothing throws,
 * and reviewing it from Phnom Penh will not reveal it either.
 */
class AnalyticsTimeZoneTest {

    /**
     * The three cases the design calls out. The third is the one that breaks under the wrong
     * single-clause conversion: 17:30Z is already the next day in Cambodia.
     */
    @ParameterizedTest(name = "{0} (UTC) buckets to {1} in Phnom Penh")
    @CsvSource({
        "2026-08-01T02:00:00Z, 2026-08-01",   // 09:00 local, same day
        "2026-08-01T16:00:00Z, 2026-08-01",   // 23:00 local, still the same day
        "2026-08-01T17:30:00Z, 2026-08-02"    // 00:30 local, next day
    })
    void bucketsInstantsToTheCorrectCambodianDay(String utcInstant, String expectedLocalDate) {
        assertThat(AnalyticsTimeZone.toLocalDate(Instant.parse(utcInstant)))
            .isEqualTo(LocalDate.parse(expectedLocalDate));
    }

    @Test
    @DisplayName("Cambodia is UTC+7, so a local day starts at 17:00 the previous UTC day")
    void localDayStartsSevenHoursEarlyInUtc() {
        assertThat(AnalyticsTimeZone.startOfDay(LocalDate.parse("2026-08-01")))
            .isEqualTo(Instant.parse("2026-07-31T17:00:00Z"));
    }

    @Test
    @DisplayName("ranges are half-open: the end is the start of the next local day")
    void endOfDayIsExclusive() {
        assertThat(AnalyticsTimeZone.endOfDayExclusive(LocalDate.parse("2026-08-01")))
            .isEqualTo(Instant.parse("2026-08-01T17:00:00Z"));
        // Adjacent days meet exactly, so nothing falls between two ranges and nothing is
        // counted twice.
        assertThat(AnalyticsTimeZone.endOfDayExclusive(LocalDate.parse("2026-08-01")))
            .isEqualTo(AnalyticsTimeZone.startOfDay(LocalDate.parse("2026-08-02")));
    }

    @Test
    @DisplayName("a whole local day spans exactly 24 hours of instants")
    void dayRangeCoversTwentyFourHours() {
        LocalDate day = LocalDate.parse("2026-08-01");
        assertThat(AnalyticsTimeZone.endOfDayExclusive(day).toEpochMilli()
            - AnalyticsTimeZone.startOfDay(day).toEpochMilli())
            .isEqualTo(24L * 60 * 60 * 1000);
    }

    /**
     * The whole point of the class. Both clauses must be present, in this order: the first
     * declares the naive column to be UTC, the second converts it to local.
     */
    @Test
    @DisplayName("the SQL fragment converts UTC to local, not the other way round")
    void sqlFragmentUsesBothAtTimeZoneClauses() {
        String sql = AnalyticsTimeZone.dayBucket("created_at");

        assertThat(sql).isEqualTo(
            "date_trunc('day', (created_at AT TIME ZONE 'UTC' AT TIME ZONE 'Asia/Phnom_Penh'))");
        assertThat(sql.indexOf("AT TIME ZONE 'UTC'"))
            .as("UTC clause must come first, or the conversion is inverted")
            .isLessThan(sql.indexOf("AT TIME ZONE 'Asia/Phnom_Penh'"));
    }

    @Test
    @DisplayName("week and month buckets convert the same way")
    void weekAndMonthUseTheSameConversion() {
        assertThat(AnalyticsTimeZone.weekBucket("created_at")).contains(
            "AT TIME ZONE 'UTC' AT TIME ZONE 'Asia/Phnom_Penh'");
        assertThat(AnalyticsTimeZone.monthBucket("created_at")).contains(
            "AT TIME ZONE 'UTC' AT TIME ZONE 'Asia/Phnom_Penh'");
    }

    @Test
    @DisplayName("today() is Cambodia's date, not the JVM default zone's")
    void todayIsCambodian() {
        assertThat(AnalyticsTimeZone.today()).isEqualTo(LocalDate.now(AnalyticsTimeZone.ZONE));
    }
}
