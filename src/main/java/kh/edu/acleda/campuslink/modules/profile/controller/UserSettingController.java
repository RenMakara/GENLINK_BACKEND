package kh.edu.acleda.campuslink.modules.profile.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.profile.dto.request.UserSettingUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.UserSettingResponse;
import kh.edu.acleda.campuslink.modules.profile.service.UserSettingService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile/settings")
@Tag(name = "Profile - Settings")
public class UserSettingController {

    private final UserSettingService userSettingService;

    public UserSettingController(UserSettingService userSettingService) {
        this.userSettingService = userSettingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserSettingResponse>> getMine() {
        return ResponseEntity.ok(ApiResponse.success("Settings retrieved", userSettingService.getMine()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserSettingResponse>> update(@Valid @RequestBody UserSettingUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Settings updated", userSettingService.update(request)));
    }
}
