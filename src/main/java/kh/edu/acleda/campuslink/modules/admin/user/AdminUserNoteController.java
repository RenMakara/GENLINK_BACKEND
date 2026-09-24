package kh.edu.acleda.campuslink.modules.admin.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserNoteRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserNoteResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only boundary for internal notes; there is intentionally no mobile equivalent. */
@Tag(name = "Admin - User Notes", description = "Internal staff notes on a user")
@RestController
@RequestMapping("/api/v1/admin/users/{userId}/notes")
@RequiredArgsConstructor
public class AdminUserNoteController {

    private final AdminUserNoteService adminUserNoteService;

    @Operation(summary = "List internal notes", description = "Returns staff-only notes newest first.")
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_user:read')")
    public ApiResponse<PageResponse<AdminUserNoteResponse>> list(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.success(
            "User notes retrieved", adminUserNoteService.list(userId, page, size));
    }

    @Operation(summary = "Add an internal note", description = "Appends a staff-only note to the user record.")
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_user:note:write')")
    public ApiResponse<AdminUserNoteResponse> create(
        @PathVariable UUID userId,
        @Valid @RequestBody AdminUserNoteRequest request) {
        return ApiResponse.success("User note added", adminUserNoteService.create(userId, request));
    }
}
