package kh.edu.acleda.campuslink.modules.productivity.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.FocusSessionStartRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusSessionResponse;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusStatsResponse;
import kh.edu.acleda.campuslink.modules.productivity.service.FocusSessionService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productivity/focus-sessions")
@Tag(name = "Productivity - Focus Sessions")
public class FocusSessionController {

    private final FocusSessionService focusSessionService;

    public FocusSessionController(FocusSessionService focusSessionService) {
        this.focusSessionService = focusSessionService;
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<FocusSessionResponse>> start(
        @Valid @RequestBody FocusSessionStartRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Focus session started", focusSessionService.start(request)));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<FocusSessionResponse>> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Focus session completed", focusSessionService.complete(id)));
    }

    /** Session history, newest first. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<FocusSessionResponse>>> history(
        @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Focus sessions retrieved",
            focusSessionService.history(pageable)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<FocusStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.success("Focus stats retrieved", focusSessionService.stats()));
    }
}
