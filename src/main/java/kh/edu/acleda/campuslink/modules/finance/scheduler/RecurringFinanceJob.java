package kh.edu.acleda.campuslink.modules.finance.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;
import kh.edu.acleda.campuslink.modules.finance.service.AutoSaveService;
import kh.edu.acleda.campuslink.modules.finance.service.RecurringTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import kh.edu.acleda.campuslink.shared.service.ScheduledJobRunRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Drains the finance automation once a day: materialises due recurring transactions, then applies due auto-save rules. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecurringFinanceJob {

    private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

    private final RecurringTransactionService recurringTransactionService;
    private final AutoSaveService autoSaveService;
    private final ScheduledJobRunRegistry scheduledJobRunRegistry;

    /** 01:15 daily, Phnom Penh time. */
    @Scheduled(cron = "${app.finance.recurring-cron:0 15 1 * * *}", zone = "Asia/Phnom_Penh")
    public void run() {
        LocalDate today = LocalDate.now(ZONE);
        boolean successful = true;

        // The two are independent: a failure in one must not skip the other, so each is guarded.
        try {
            int occurrences = recurringTransactionService.processDue(today);
            if (occurrences > 0) {
                log.info("Recurring transactions run: {} occurrence(s) materialised", occurrences);
            }
        } catch (Exception ex) {
            successful = false;
            log.error("Recurring transactions run failed: {}", ex.getMessage(), ex);
        }

        try {
            int applied = autoSaveService.processDue(today);
            if (applied > 0) {
                log.info("Auto-save run: {} rule(s) moved money", applied);
            }
        } catch (Exception ex) {
            successful = false;
            log.error("Auto-save run failed: {}", ex.getMessage(), ex);
        }

        if (successful) {
            scheduledJobRunRegistry.recordSuccess(ScheduledJobRunRegistry.RECURRING_FINANCE_JOB);
        } else {
            scheduledJobRunRegistry.recordFailure(ScheduledJobRunRegistry.RECURRING_FINANCE_JOB);
        }
    }
}
