package kh.edu.acleda.campuslink.modules.admin.sanction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.annotation.Scheduled;

class SuspensionExpiryJobTest {

    @Test
    void scheduledRunDelegatesWithTheCurrentInstant() {
        SuspensionExpiryService service = Mockito.mock(SuspensionExpiryService.class);
        when(service.expireSuspensions(any(Instant.class))).thenReturn(2);

        new SuspensionExpiryJob(service).run();

        verify(service).expireSuspensions(any(Instant.class));
    }

    @Test
    void scheduledFailureIsLoggedAndDoesNotEscapeTheScheduler() {
        SuspensionExpiryService service = Mockito.mock(SuspensionExpiryService.class);
        doThrow(new IllegalStateException("database failure"))
            .when(service).expireSuspensions(any(Instant.class));

        Assertions.assertThatCode(() -> new SuspensionExpiryJob(service).run())
            .doesNotThrowAnyException();
    }

    @Test
    void usesTheConfigurablePhnomPenhDailySchedule() throws Exception {
        Method method = SuspensionExpiryJob.class.getMethod("run");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        Assertions.assertThat(scheduled).isNotNull();
        Assertions.assertThat(scheduled.cron())
            .isEqualTo("${app.admin.suspension-expiry-cron:0 0 3 * * *}");
        Assertions.assertThat(scheduled.zone()).isEqualTo("Asia/Phnom_Penh");
    }
}
