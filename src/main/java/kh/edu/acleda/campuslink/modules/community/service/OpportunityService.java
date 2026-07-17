package kh.edu.acleda.campuslink.modules.community.service;

import kh.edu.acleda.campuslink.modules.community.dto.request.OpportunityCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.OpportunityResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OpportunityService {

    /** Browse active opportunities. type is optional; includeExpired shows past ones too. */
    PageResponse<OpportunityResponse> browse(String type, boolean includeExpired, Pageable pageable);

    OpportunityResponse getById(UUID opportunityId);

    OpportunityResponse create(OpportunityCreateRequest request);

    OpportunityResponse update(UUID opportunityId, OpportunityCreateRequest request);

    void delete(UUID opportunityId);
}
