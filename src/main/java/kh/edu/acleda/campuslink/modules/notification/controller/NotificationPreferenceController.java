package kh.edu.acleda.campuslink.modules.notification.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kh.edu.acleda.campuslink.modules.notification.dto.request.NotificationPreferenceUpdateRequest;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationPreferenceResponse;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationPreferenceService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/profile/settings/notifications")
@Tag(name = "Profile - Notification Settings")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<List<NotificationPreferenceResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success(
            "Notification preferences retrieved", preferenceService.getMine()));
    }

    @PutMapping("/preferences/{notificationType}")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> update(
        @PathVariable String notificationType,
        @Valid @RequestBody NotificationPreferenceUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
            "Notification preference updated", preferenceService.update(notificationType, request)));
    }
}
