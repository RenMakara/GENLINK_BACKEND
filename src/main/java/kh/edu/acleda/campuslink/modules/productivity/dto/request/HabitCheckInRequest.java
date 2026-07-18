package kh.edu.acleda.campuslink.modules.productivity.dto.request;

import java.time.LocalDate;

/**
 * @param date      the day being checked in; defaults to today when omitted
 * @param completed true to mark done (default), false to undo a check-in for that day
 */
public record HabitCheckInRequest(
    LocalDate date,
    Boolean completed
) {
}
