package kh.edu.acleda.campuslink.modules.admin.sanction;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the suspension expiry sweep once per day in Phnom Penh time. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuspensionExpiryJob {

    private final SuspensionExpiryService suspensionExpiryService;

    /** Runs daily at 03:00 Phnom Penh time. */
    @Scheduled(cron = "${app.admin.suspension-expiry-cron:0 0 3 * * *}",
        zone = "Asia/Phnom_Penh")
    public void run() {
        try {
            suspensionExpiryService.expireSuspensions(Instant.now());
        } catch (Exception ex) {
            // Keep the scheduler alive and make a failed sweep visible to operators. Expired
            // rows remain open and are retried on the next run.
            log.error("Suspension expiry run failed: {}", ex.getMessage(), ex);
        }
    }
}
