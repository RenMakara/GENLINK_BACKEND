package kh.edu.acleda.campuslink.modules.media;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kh.edu.acleda.campuslink.shared.service.ScheduledJobRunRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaCleanupJobTest {

    @Mock
    private MediaCleanupService cleanupService;

    @Mock
    private ScheduledJobRunRegistry registry;

    @Test
    void cleanSweepsRecordSuccess() {
        when(cleanupService.purgeOrphans()).thenReturn(new MediaCleanupService.CleanupResult(2, 0, 4, false));
        when(cleanupService.purgeSoftDeleted()).thenReturn(new MediaCleanupService.CleanupResult(1, 0, 3, false));

        new MediaCleanupJob(cleanupService, registry).cleanUp();

        verify(registry).recordSuccess(ScheduledJobRunRegistry.MEDIA_CLEANUP_JOB);
    }

    @Test
    void failedDeletesOrSweepsRecordFailureWithoutThrowing() {
        when(cleanupService.purgeOrphans()).thenReturn(new MediaCleanupService.CleanupResult(0, 1, 4, false));
        doThrow(new IllegalStateException("storage failure"))
            .when(cleanupService).purgeSoftDeleted();

        new MediaCleanupJob(cleanupService, registry).cleanUp();

        verify(registry).recordFailure(ScheduledJobRunRegistry.MEDIA_CLEANUP_JOB);
    }
}
