package kh.edu.acleda.campuslink.modules.productivity;

import java.time.LocalDateTime;
import kh.edu.acleda.campuslink.modules.productivity.scheduler.TaskReminderJob;
import kh.edu.acleda.campuslink.modules.productivity.service.TaskReminderService;
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
class TaskReminderJobTest {

    @Mock private TaskReminderService taskReminderService;

    @Test
    void scheduledRunDelegatesWithoutAUserContext() {
        when(taskReminderService.sendDueReminders(any(LocalDateTime.class))).thenReturn(2);

        new TaskReminderJob(taskReminderService).run();

        verify(taskReminderService).sendDueReminders(any(LocalDateTime.class));
    }

    @Test
    void scheduledRunSwallowsAServiceFailureSoSchedulingContinues() {
        when(taskReminderService.sendDueReminders(any(LocalDateTime.class)))
            .thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(() -> new TaskReminderJob(taskReminderService).run())
            .doesNotThrowAnyException();
    }

    @Test
    void scheduleUsesTheConfigurableFiveMinutePhnomPenhCron() throws NoSuchMethodException {
        Scheduled scheduled = TaskReminderJob.class.getMethod("run").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${app.productivity.task-reminder-cron:0 */5 * * * *}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Phnom_Penh");
    }
}
