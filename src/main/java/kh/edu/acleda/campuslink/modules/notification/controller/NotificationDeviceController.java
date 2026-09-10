package kh.edu.acleda.campuslink.modules.notification.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.dto.request.NotificationDeviceRegistrationRequest;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationDeviceResponse;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationDeviceService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/profile/settings/notifications/devices")
@Tag(name = "Profile - Notification Devices")
public class NotificationDeviceController {

    private final NotificationDeviceService deviceService;

    public NotificationDeviceController(NotificationDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationDeviceResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success(
            "Notification devices retrieved", deviceService.getMine()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<NotificationDeviceResponse>> register(
        @Valid @RequestBody NotificationDeviceRegistrationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
            "Notification device registered", deviceService.register(request)));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID deviceId) {
        deviceService.deactivate(deviceId);
        return ResponseEntity.ok(ApiResponse.success("Notification device deactivated", null));
    }
}
