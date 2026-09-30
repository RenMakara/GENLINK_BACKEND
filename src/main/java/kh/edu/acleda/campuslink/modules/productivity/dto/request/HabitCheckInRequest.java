package kh.edu.acleda.campuslink.modules.productivity.dto.request;

import java.time.LocalDate;

/** @param date the day being checked in; defaults to today when omitted */
public record HabitCheckInRequest(
    LocalDate date,
    Boolean completed
) {
}
