package kh.edu.acleda.campuslink.modules.media;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the media sweeps once a day.
 *
 * <p>Separate from {@link MediaCleanupService} so the sweep logic stays directly
 * callable and testable without a scheduler in the way — the same split as
 * AccountAnonymizationJob.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaCleanupJob {

    private final MediaCleanupService mediaCleanupService;

    /**
     * 03:45 daily, fifteen minutes after the account sweep so the two never
     * contend for the same rows: anonymisation soft-deletes a departed user's
     * media, and this run is then free to purge whatever has aged out.
     *
     * <p>The zone is pinned rather than inherited so the schedule cannot drift if
     * the container timezone changes. Overridable to retime without a redeploy.
     */
    @Scheduled(cron = "${app.media.cleanup-cron:0 45 3 * * *}", zone = "Asia/Phnom_Penh")
    public void cleanUp() {
        // Each sweep is caught separately: a failure in one must not stop the
        // other, and a scheduled method that throws just looks like a quiet run.
        runQuietly("orphan", mediaCleanupService::purgeOrphans);
        runQuietly("purge", mediaCleanupService::purgeSoftDeleted);
    }

    private void runQuietly(String sweep, java.util.function.IntSupplier action) {
        try {
            int count = action.getAsInt();
            if (count > 0) {
                log.info("Media {} sweep finished: {} row(s) removed", sweep, count);
            }
        } catch (Exception ex) {
            log.error("Media {} sweep failed: {}", sweep, ex.getMessage(), ex);
        }
    }
}
