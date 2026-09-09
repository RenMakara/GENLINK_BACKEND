package kh.edu.acleda.campuslink.modules.productivity.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;
import kh.edu.acleda.campuslink.modules.productivity.service.TaskReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the task reminder sweep without a user/request context. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskReminderJob {

    private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

    private final TaskReminderService taskReminderService;

    /**
     * Every five minutes, Phnom Penh time. The overlap around the configured reminder time is
     * safe because the notification writer enforces the recipient-scoped dedupe key.
     */
    @Scheduled(cron = "${app.productivity.task-reminder-cron:0 */5 * * * *}", zone = "Asia/Phnom_Penh")
    public void run() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        try {
            int reminders = taskReminderService.sendDueReminders(now);
            if (reminders > 0) {
                log.info("Task reminder run: {} reminder(s) emitted", reminders);
            }
        } catch (Exception ex) {
            log.error("Task reminder run failed: {}", ex.getMessage(), ex);
        }
    }
}
