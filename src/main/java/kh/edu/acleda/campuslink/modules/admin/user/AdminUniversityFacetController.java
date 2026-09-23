package kh.edu.acleda.campuslink.modules.admin.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Permission-gated university facet boundary for the admin user directory. */
@Tag(name = "Admin - Users", description = "Search, filter and paginate user records")
@RestController
@RequestMapping("/api/v1/admin/users/facets")
@RequiredArgsConstructor
public class AdminUniversityFacetController {

    private final AdminUniversityFacetService adminUniversityFacetService;

    @Operation(
        summary = "List university facet values",
        description = "Returns up to 50 distinct live university names, ordered by frequency."
            + " An optional q parameter filters the typeahead values.")
    @GetMapping("/universities")
    @PreAuthorize("hasAuthority('PERM_user:read')")
    public ApiResponse<List<String>> listUniversities(
        @RequestParam(defaultValue = "") String q) {
        return ApiResponse.success("Admin university facets retrieved",
            adminUniversityFacetService.listUniversities(q));
    }
}
