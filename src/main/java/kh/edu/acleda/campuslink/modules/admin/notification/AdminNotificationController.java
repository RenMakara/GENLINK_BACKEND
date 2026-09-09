package kh.edu.acleda.campuslink.modules.admin.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminBroadcastRequest;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminNotificationTemplateCreateRequest;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminNotificationTemplateUpdateRequest;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.response.AdminBroadcastResponse;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.response.AdminNotificationTemplateResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only notification templates and bounded in-app broadcasts. */
@Tag(name = "Admin - Notifications", description = "Audited notification templates and broadcasts")
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    @Operation(summary = "List admin broadcast templates",
        description = "Returns only mutable ADMIN_ templates. Module-trigger templates remain Liquibase-owned.")
    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('PERM_notification:template:manage')")
    public ApiResponse<List<AdminNotificationTemplateResponse>> listTemplates() {
        return ApiResponse.success("Admin notification templates retrieved",
            adminNotificationService.listTemplates());
    }

    @Operation(summary = "Create an admin broadcast template",
        description = "Creates a bilingual template in the reserved ADMIN_ namespace. The key is immutable.")
    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('PERM_notification:template:manage')")
    public ApiResponse<AdminNotificationTemplateResponse> createTemplate(
        @Valid @RequestBody AdminNotificationTemplateCreateRequest request) {
        return ApiResponse.success("Admin notification template created",
            adminNotificationService.createTemplate(request));
    }

    @Operation(summary = "Update an admin broadcast template",
        description = "Updates localized text only. Domain-trigger templates cannot be changed here.")
    @PutMapping("/templates/{templateKey}")
    @PreAuthorize("hasAuthority('PERM_notification:template:manage')")
    public ApiResponse<AdminNotificationTemplateResponse> updateTemplate(
        @PathVariable String templateKey,
        @Valid @RequestBody AdminNotificationTemplateUpdateRequest request) {
        return ApiResponse.success("Admin notification template updated",
            adminNotificationService.updateTemplate(templateKey, request));
    }

    @Operation(summary = "Send an in-app broadcast",
        description = "Sends one existing ADMIN_ template to ALL_ACTIVE or up to 500 explicitly selected active users.")
    @PostMapping("/broadcast")
    @PreAuthorize("hasAuthority('PERM_notification:send')")
    public ApiResponse<AdminBroadcastResponse> broadcast(
        @Valid @RequestBody AdminBroadcastRequest request) {
        return ApiResponse.success("Admin notification broadcast accepted",
            adminNotificationService.broadcast(request));
    }
}
