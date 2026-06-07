package kh.edu.acleda.campuslink.modules.notification.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationResponse;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved", notificationService.getMine()));
    }

    @PostMapping("/<built-in function id>/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID id) {
        notificationService.markRead(id);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
    }
}
