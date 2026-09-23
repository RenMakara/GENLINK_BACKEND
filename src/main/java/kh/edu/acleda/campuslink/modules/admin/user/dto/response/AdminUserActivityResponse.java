package kh.edu.acleda.campuslink.modules.admin.user.dto.response;

import java.util.UUID;

/** Safe, read-only cross-domain activity counters for one admin user detail page. */
public record AdminUserActivityResponse(
    UUID userId,
    long posts,
    long comments,
    long communitiesJoined,
    long eventsRsvpd,
    long reportsFiled,
    long reportsReceived
) {
}
