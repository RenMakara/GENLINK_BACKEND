package kh.edu.acleda.campuslink.modules.finance.enums;

import java.time.LocalDate;

/** How often a recurring transaction materialises. Each value knows how to advance a date. */
public enum RecurringFrequency {
    DAILY {
        @Override
        public LocalDate next(LocalDate from) {
            return from.plusDays(1);
        }
    },
    WEEKLY {
        @Override
        public LocalDate next(LocalDate from) {
            return from.plusWeeks(1);
        }
    },
    MONTHLY {
        @Override
        public LocalDate next(LocalDate from) {
            return from.plusMonths(1);
        }
    };

    /** The next run date after {@code from} for this frequency. */
    public abstract LocalDate next(LocalDate from);
}
