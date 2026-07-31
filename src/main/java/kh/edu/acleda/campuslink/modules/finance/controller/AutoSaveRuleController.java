package kh.edu.acleda.campuslink.modules.finance.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.AutoSaveRuleCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.AutoSaveRuleResponse;
import kh.edu.acleda.campuslink.modules.finance.service.AutoSaveService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/auto-save")
@Tag(name = "Finance - Auto-Save Rules")
public class AutoSaveRuleController {

    private final AutoSaveService autoSaveService;

    public AutoSaveRuleController(AutoSaveService autoSaveService) {
        this.autoSaveService = autoSaveService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AutoSaveRuleResponse>> create(
        @Valid @RequestBody AutoSaveRuleCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Auto-save rule created",
            autoSaveService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AutoSaveRuleResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success("Auto-save rules retrieved",
            autoSaveService.getMine()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        autoSaveService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Auto-save rule deleted", null));
    }
}
