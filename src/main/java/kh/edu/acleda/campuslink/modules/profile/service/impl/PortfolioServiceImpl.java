package kh.edu.acleda.campuslink.modules.profile.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.dto.request.PortfolioItemCreateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.request.PortfolioItemUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.PortfolioItemResponse;
import kh.edu.acleda.campuslink.modules.profile.entity.PortfolioItem;
import kh.edu.acleda.campuslink.modules.profile.repository.PortfolioItemRepository;
import kh.edu.acleda.campuslink.modules.profile.service.PortfolioService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioServiceImpl implements PortfolioService {

    private final PortfolioItemRepository portfolioItemRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public PortfolioServiceImpl(PortfolioItemRepository portfolioItemRepository,
                                UserRepository userRepository,
                                CurrentUserProvider currentUserProvider) {
        this.portfolioItemRepository = portfolioItemRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioItemResponse> getMine() {
        return getByUserId(currentUserProvider.getCurrentUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioItemResponse> getByUserId(UUID userId) {
        return portfolioItemRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByUserId(UUID userId) {
        return portfolioItemRepository.countByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    @Transactional
    public PortfolioItemResponse create(PortfolioItemCreateRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));

        PortfolioItem item = PortfolioItem.builder()
            .user(user)
            .title(request.title())
            .description(request.description())
            .link(request.link())
            .imageUrl(request.imageUrl())
            .build();

        return toResponse(portfolioItemRepository.save(item));
    }

    @Override
    @Transactional
    public PortfolioItemResponse update(UUID portfolioId, PortfolioItemUpdateRequest request) {
        PortfolioItem item = loadOwned(portfolioId);

        if (request.title() != null) {
            item.setTitle(request.title());
        }
        if (request.description() != null) {
            item.setDescription(request.description());
        }
        if (request.link() != null) {
            item.setLink(request.link());
        }
        if (request.imageUrl() != null) {
            item.setImageUrl(request.imageUrl());
        }

        return toResponse(portfolioItemRepository.save(item));
    }

    @Override
    @Transactional
    public void delete(UUID portfolioId) {
        PortfolioItem item = loadOwned(portfolioId);
        item.setDeletedAt(Instant.now());
        portfolioItemRepository.save(item);
    }

    /**
     * Scoping the lookup by owner means another user's id resolves to "not found"
     * rather than leaking that the row exists.
     */
    private PortfolioItem loadOwned(UUID portfolioId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        return portfolioItemRepository.findByIdAndUserIdAndDeletedAtIsNull(portfolioId, userId)
            .orElseThrow(() -> new NotFoundException("Portfolio item not found"));
    }

    private PortfolioItemResponse toResponse(PortfolioItem item) {
        return new PortfolioItemResponse(
            item.getId(),
            item.getTitle(),
            item.getDescription(),
            item.getLink(),
            item.getImageUrl()
        );
    }
}
