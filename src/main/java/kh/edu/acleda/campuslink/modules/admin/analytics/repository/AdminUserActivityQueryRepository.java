package kh.edu.acleda.campuslink.modules.admin.analytics.repository;

import java.util.UUID;

/**
 * Read-only cross-domain projection for the activity tab on an admin user detail page.
 *
 * <p>This repository is deliberately kept under the admin analytics boundary. The admin
 * module must not depend on community repositories just to assemble a read-only projection.
 */
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
