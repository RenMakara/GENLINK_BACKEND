package kh.edu.acleda.campuslink.modules.notification.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationResponse;
import kh.edu.acleda.campuslink.modules.notification.dto.response.UnreadCountResponse;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Newest first. {@code ?unreadOnly=true} narrows to unread. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getMine(
        @RequestParam(defaultValue = "false") boolean unreadOnly,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved",
            notificationService.getMine(unreadOnly, pageable)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved",
            new UnreadCountResponse(notificationService.unreadCount())));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID id) {
        notificationService.markRead(id);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead() {
        int changed = notificationService.markAllRead();
        return ResponseEntity.ok(ApiResponse.success(changed + " notification(s) marked as read", null));
    }
}
