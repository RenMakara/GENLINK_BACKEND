package kh.edu.acleda.campuslink.modules.media;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.media.dto.ConfirmRequest;
import kh.edu.acleda.campuslink.modules.media.dto.MediaResponse;
import kh.edu.acleda.campuslink.modules.media.dto.PresignRequest;
import kh.edu.acleda.campuslink.modules.media.dto.PresignResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Media endpoints. All require authentication (via the {@code /api/v1/**} rule in
 * SecurityConfig). Bytes never flow through here — the client uploads directly to
 * object storage using the presigned URL this controller mints.
 */
@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<PresignResponse>> presign(@Valid @RequestBody PresignRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Upload URL generated",
            mediaService.presignUpload(request)));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<MediaResponse>> confirm(@Valid @RequestBody ConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Media confirmed",
            mediaService.confirmUpload(request)));
    }

    @GetMapping("/{id}/url")
    public ResponseEntity<ApiResponse<Map<String, String>>> url(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Media URL",
            Map.of("url", mediaService.downloadUrl(id))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        mediaService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Media deleted", null));
    }
}
