package kh.edu.acleda.campuslink.modules.notification.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kh.edu.acleda.campuslink.modules.notification.dto.request.NotificationPreferenceUpdateRequest;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationChannelPreferenceResponse;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannelPreferenceService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/profile/settings/notifications")
@Tag(name = "Profile - Notification Channel Settings")
public class NotificationChannelPreferenceController {

    private final NotificationChannelPreferenceService preferenceService;

    public NotificationChannelPreferenceController(
        NotificationChannelPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping("/channels")
    public ResponseEntity<ApiResponse<List<NotificationChannelPreferenceResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success(
            "Notification channel preferences retrieved", preferenceService.getMine()));
    }

    @PutMapping("/channels/{channel}")
    public ResponseEntity<ApiResponse<NotificationChannelPreferenceResponse>> update(
        @PathVariable String channel,
        @Valid @RequestBody NotificationPreferenceUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
            "Notification channel preference updated", preferenceService.update(channel, request)));
    }
}
