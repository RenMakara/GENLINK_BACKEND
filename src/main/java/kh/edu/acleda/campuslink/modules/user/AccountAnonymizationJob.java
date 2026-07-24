package kh.edu.acleda.campuslink.modules.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link AccountAnonymizationService} once a day.
 *
 * <p>Separate from the service so the scrubbing logic stays directly callable and
 * testable without a scheduler in the way.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountAnonymizationJob {

    private final AccountAnonymizationService accountAnonymizationService;

    /**
     * 03:30 daily — off-peak for a Cambodian audience. The zone is pinned rather than
     * inherited so the schedule does not silently shift if the container's TZ changes.
     *
     * <p>Overridable so an operator can retime or effectively disable it (by pointing it
     * at a date that never occurs) without a redeploy.
     */
    @Scheduled(cron = "${app.account.anonymization-cron:0 30 3 * * *}", zone = "Asia/Phnom_Penh")
    public void anonymizeExpiredAccounts() {
        try {
            int count = accountAnonymizationService.anonymizeExpiredAccounts();
            if (count > 0) {
                log.info("Account anonymisation run finished: {} account(s) scrubbed", count);
            }
        } catch (Exception ex) {
            // A scheduled method that throws is logged by Spring but silently stops
            // contributing anything useful; catching here keeps one bad row from
            // looking like a healthy no-op run.
            log.error("Account anonymisation run failed: {}", ex.getMessage(), ex);
        }
    }
}
