package kh.edu.acleda.campuslink.modules.profile.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.dto.request.PortfolioItemCreateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.request.PortfolioItemUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.PortfolioItemResponse;
import kh.edu.acleda.campuslink.modules.profile.service.PortfolioService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile/portfolio")
@Tag(name = "Profile - Portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PortfolioItemResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success("Portfolio retrieved", portfolioService.getMine()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PortfolioItemResponse>> create(
        @Valid @RequestBody PortfolioItemCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Portfolio item created", portfolioService.create(request)));
    }

    @PutMapping("/{portfolioId}")
    public ResponseEntity<ApiResponse<PortfolioItemResponse>> update(
        @PathVariable UUID portfolioId,
        @Valid @RequestBody PortfolioItemUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Portfolio item updated", portfolioService.update(portfolioId, request)));
    }

    @DeleteMapping("/{portfolioId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID portfolioId) {
        portfolioService.delete(portfolioId);
        return ResponseEntity.ok(ApiResponse.success("Portfolio item deleted", null));
    }
}
