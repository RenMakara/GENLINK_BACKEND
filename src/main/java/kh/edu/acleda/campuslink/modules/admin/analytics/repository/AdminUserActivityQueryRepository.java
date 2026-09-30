package kh.edu.acleda.campuslink.modules.admin.analytics.repository;

import java.util.UUID;

/** Read-only cross-domain projection for the activity tab on an admin user detail page. */
public interface AdminUserActivityQueryRepository {

    UserActivityCounts findByUserId(UUID userId);

    record UserActivityCounts(
        boolean userExists,
        long posts,
        long comments,
        long communitiesJoined,
        long eventsRsvpd,
        long reportsFiled,
        long reportsReceived
    ) {
    }
}
