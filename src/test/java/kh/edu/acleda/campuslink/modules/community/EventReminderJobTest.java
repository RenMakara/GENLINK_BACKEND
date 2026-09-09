package kh.edu.acleda.campuslink.modules.community;

import java.time.LocalDateTime;
import kh.edu.acleda.campuslink.modules.community.scheduler.EventReminderJob;
import kh.edu.acleda.campuslink.modules.community.service.EventReminderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventReminderJobTest {

    @Mock private EventReminderService eventReminderService;

    @Test
    void scheduledRunDelegatesWithoutAUserContext() {
        when(eventReminderService.sendDueReminders(any(LocalDateTime.class))).thenReturn(2);

        new EventReminderJob(eventReminderService).run();

        verify(eventReminderService).sendDueReminders(any(LocalDateTime.class));
    }

    @Test
    void scheduledRunSwallowsAServiceFailureSoSchedulingContinues() {
        when(eventReminderService.sendDueReminders(any(LocalDateTime.class)))
            .thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(() -> new EventReminderJob(eventReminderService).run())
            .doesNotThrowAnyException();
    }

    @Test
    void scheduleUsesTheConfigurableFiveMinutePhnomPenhCron() throws NoSuchMethodException {
        Scheduled scheduled = EventReminderJob.class.getMethod("run").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${app.community.event-reminder-cron:0 */5 * * * *}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Phnom_Penh");
    }
}
