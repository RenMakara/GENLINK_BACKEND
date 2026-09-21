package kh.edu.acleda.campuslink.modules.productivity.service;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.FocusSessionStartRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusSessionResponse;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusStatsResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface FocusSessionService {

    /** Begin a focus session. No XP until it is completed. */
    FocusSessionResponse start(FocusSessionStartRequest request);

    /** Return the user's current active or paused session, if one exists. */
    FocusSessionResponse active();

    FocusSessionResponse pause(UUID id);

    FocusSessionResponse resume(UUID id);

    /** Complete a session; awards focused-duration-scaled XP once. */
    FocusSessionResponse complete(UUID id);

    FocusSessionResponse abandon(UUID id);

    PageResponse<FocusSessionResponse> history(Pageable pageable);

    FocusStatsResponse stats();
}
