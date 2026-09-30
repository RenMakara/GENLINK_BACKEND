package kh.edu.acleda.campuslink.modules.media;

import kh.edu.acleda.campuslink.modules.media.MediaCleanupService.CleanupResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import kh.edu.acleda.campuslink.shared.service.ScheduledJobRunRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs the media sweeps once a day. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaCleanupJob {

    private final MediaCleanupService mediaCleanupService;
    private final ScheduledJobRunRegistry scheduledJobRunRegistry;

    /** 03:45 daily, fifteen minutes after the account sweep. */
    @Scheduled(cron = "${app.media.cleanup.cron:0 45 3 * * *}", zone = "Asia/Phnom_Penh")
    public void cleanUp() {
        // Each sweep is caught separately: a failure in one must not stop the
        // other, and a scheduled method that throws just looks like a quiet run.
        AtomicBoolean runFailed = new AtomicBoolean();
        CleanupResult orphans = runQuietly("orphans", mediaCleanupService::purgeOrphans, runFailed);
        CleanupResult purged = runQuietly("purge", mediaCleanupService::purgeSoftDeleted, runFailed);

        // One summary line per run, always emitted.
        log.info("Media cleanup run: orphans[deleted={} failed={} {}ms{}] "
                + "purge[deleted={} failed={} {}ms{}] total={}ms",
            orphans.deleted(), orphans.failed(), orphans.durationMs(), more(orphans),
            purged.deleted(), purged.failed(), purged.durationMs(), more(purged),
            orphans.durationMs() + purged.durationMs());

        // Failures mean bytes are still in the bucket.
        int failed = orphans.failed() + purged.failed();
        if (failed > 0) {
            log.warn("Media cleanup left {} object(s) undeleted; the next run retries them", failed);
        }

        if (runFailed.get() || failed > 0) {
            scheduledJobRunRegistry.recordFailure(ScheduledJobRunRegistry.MEDIA_CLEANUP_JOB);
        } else {
            scheduledJobRunRegistry.recordSuccess(ScheduledJobRunRegistry.MEDIA_CLEANUP_JOB);
        }
    }

    /** " +more" when the sweep hit its batch ceiling and work remains. */
    private String more(CleanupResult result) {
        return result.batchFull() ? " +more" : "";
    }

    private CleanupResult runQuietly(String sweep, Supplier<CleanupResult> action,
                                     AtomicBoolean runFailed) {
        try {
            return action.get();
        } catch (Exception ex) {
            runFailed.set(true);
            log.error("Media {} sweep failed: {}", sweep, ex.getMessage(), ex);
            return CleanupResult.empty(0);
        }
    }
}
