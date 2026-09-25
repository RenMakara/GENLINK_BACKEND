package kh.edu.acleda.campuslink.modules.admin.analytics.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TtlCacheTest {

    @Test
    void expiresEntriesAfterFiveMinutesAndLoadsEachMissOnce() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-26T00:00:00Z"));
        TtlCache cache = new TtlCache("test", Duration.ofMinutes(5), clock);
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.get("key", () -> {
            loads.incrementAndGet();
            return "first";
        })).isEqualTo("first");
        assertThat(cache.get("key", () -> {
            loads.incrementAndGet();
            return "second";
        })).isEqualTo("first");
        assertThat(loads).hasValue(1);

        clock.advance(Duration.ofMinutes(5));

        assertThat(cache.get("key", () -> {
            loads.incrementAndGet();
            return "after-expiry";
        })).isEqualTo("after-expiry");
        assertThat(loads).hasValue(2);
    }

    @Test
    void supportsEvictionAndClear() {
        TtlCache cache = new TtlCache(
            "test", Duration.ofMinutes(5), new MutableClock(Instant.EPOCH));
        cache.put("one", 1);
        cache.put("two", 2);

        cache.evict("one");
        assertThat(cache.get("one")).isNull();
        assertThat(cache.get("two", Integer.class)).isEqualTo(2);

        cache.clear();
        assertThat(cache.get("two")).isNull();
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
