package kh.edu.acleda.campuslink.modules.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import kh.edu.acleda.campuslink.shared.service.ScheduledJobRunRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs {@link AccountAnonymizationService} once a day. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountAnonymizationJob {

    private final AccountAnonymizationService accountAnonymizationService;
    private final ScheduledJobRunRegistry scheduledJobRunRegistry;

    /** 03:30 daily — off-peak for a Cambodian audience. */
    @Scheduled(cron = "${app.account.anonymization-cron:0 30 3 * * *}", zone = "Asia/Phnom_Penh")
    public void anonymizeExpiredAccounts() {
        try {
            int count = accountAnonymizationService.anonymizeExpiredAccounts();
            if (count > 0) {
                log.info("Account anonymisation run finished: {} account(s) scrubbed", count);
            }
            scheduledJobRunRegistry.recordSuccess(ScheduledJobRunRegistry.ACCOUNT_ANONYMIZATION_JOB);
        } catch (Exception ex) {
            // A scheduled method that throws is logged by Spring.
            log.error("Account anonymisation run failed: {}", ex.getMessage(), ex);
            scheduledJobRunRegistry.recordFailure(ScheduledJobRunRegistry.ACCOUNT_ANONYMIZATION_JOB);
        }
    }
}
