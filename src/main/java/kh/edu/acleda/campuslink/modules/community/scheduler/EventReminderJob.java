package kh.edu.acleda.campuslink.modules.community.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;
import kh.edu.acleda.campuslink.modules.community.service.EventReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the event reminder sweep without a user/request context. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventReminderJob {

    private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

    private final EventReminderService eventReminderService;

    /**
     * Every five minutes, Phnom Penh time. The small overlap around the 24-hour mark is safe
     * because the notification writer enforces the recipient-scoped dedupe key.
     */
    @Scheduled(cron = "${app.community.event-reminder-cron:0 */5 * * * *}", zone = "Asia/Phnom_Penh")
    public void run() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        try {
            int reminders = eventReminderService.sendDueReminders(now);
            if (reminders > 0) {
                log.info("Event reminder run: {} reminder(s) emitted", reminders);
            }
        } catch (Exception ex) {
            log.error("Event reminder run failed: {}", ex.getMessage(), ex);
        }
    }
}
