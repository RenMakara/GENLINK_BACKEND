package kh.edu.acleda.campuslink.modules.profile.service;

import kh.edu.acleda.campuslink.modules.profile.dto.request.PortfolioItemCreateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.request.PortfolioItemUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.PortfolioItemResponse;

import java.util.List;
import java.util.UUID;

public interface PortfolioService {
    List<PortfolioItemResponse> getMine();

    List<PortfolioItemResponse> getByUserId(UUID userId);

    PortfolioItemResponse create(PortfolioItemCreateRequest request);

    PortfolioItemResponse update(UUID portfolioId, PortfolioItemUpdateRequest request);

    void delete(UUID portfolioId);
}
