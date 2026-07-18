package kh.edu.acleda.campuslink.modules.productivity.service;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.FocusSessionStartRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusSessionResponse;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusStatsResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface FocusSessionService {

    /** Begin a focus session (Pomodoro). No XP until it is completed. */
    FocusSessionResponse start(FocusSessionStartRequest request);

    /** Complete a session; awards duration-scaled XP once. */
    FocusSessionResponse complete(UUID id);

    PageResponse<FocusSessionResponse> history(Pageable pageable);

    FocusStatsResponse stats();
}
