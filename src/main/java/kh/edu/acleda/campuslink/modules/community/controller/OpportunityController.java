package kh.edu.acleda.campuslink.modules.community.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.OpportunityCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.OpportunityResponse;
import kh.edu.acleda.campuslink.modules.community.service.OpportunityService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Opportunity Hub (internships, scholarships, hackathons, jobs, volunteering). */
@RestController
@RequestMapping("/api/v1/community/opportunities")
@Tag(name = "Community - Opportunities")
public class OpportunityController {

    private final OpportunityService opportunityService;

    public OpportunityController(OpportunityService opportunityService) {
        this.opportunityService = opportunityService;
    }

    /** Active opportunities, soonest deadline first. ?type= and ?includeExpired=true. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OpportunityResponse>>> browse(
        @RequestParam(required = false) String type,
        @RequestParam(defaultValue = "false") boolean includeExpired,
        @PageableDefault(size = 20, sort = "deadline", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Opportunities retrieved",
            opportunityService.browse(type, includeExpired, pageable)));
    }

    @GetMapping("/{opportunityId}")
    public ResponseEntity<ApiResponse<OpportunityResponse>> getById(@PathVariable UUID opportunityId) {
        return ResponseEntity.ok(ApiResponse.success("Opportunity retrieved",
            opportunityService.getById(opportunityId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OpportunityResponse>> create(
        @Valid @RequestBody OpportunityCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Opportunity created", opportunityService.create(request)));
    }

    @PutMapping("/{opportunityId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OpportunityResponse>> update(
        @PathVariable UUID opportunityId, @Valid @RequestBody OpportunityCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Opportunity updated",
            opportunityService.update(opportunityId, request)));
    }

    @DeleteMapping("/{opportunityId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID opportunityId) {
        opportunityService.delete(opportunityId);
        return ResponseEntity.ok(ApiResponse.success("Opportunity deleted", null));
    }
}
