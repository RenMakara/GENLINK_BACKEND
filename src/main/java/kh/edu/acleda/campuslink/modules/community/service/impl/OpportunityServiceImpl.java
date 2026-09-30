package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kh.edu.acleda.campuslink.modules.community.dto.request.OpportunityCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.OpportunityResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Opportunity;
import kh.edu.acleda.campuslink.modules.community.repository.OpportunityRepository;
import kh.edu.acleda.campuslink.modules.community.service.OpportunityService;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import kh.edu.acleda.campuslink.modules.profile.repository.SavedItemRepository;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpportunityServiceImpl implements OpportunityService {

    private final OpportunityRepository opportunityRepository;
    private final SavedItemRepository savedItemRepository;
    private final CurrentUserProvider currentUserProvider;

    public OpportunityServiceImpl(OpportunityRepository opportunityRepository,
                                  SavedItemRepository savedItemRepository,
                                  CurrentUserProvider currentUserProvider) {
        this.opportunityRepository = opportunityRepository;
        this.savedItemRepository = savedItemRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OpportunityResponse> browse(String type, boolean includeExpired, Pageable pageable) {
        // Two methods rather than one with a nullable date param.
        Page<Opportunity> page = includeExpired
            ? opportunityRepository.browseAll(type, pageable)
            : opportunityRepository.browseActive(type, LocalDate.now(), pageable);
        return hydrate(page);
    }

    @Override
    @Transactional(readOnly = true)
    public OpportunityResponse getById(UUID opportunityId) {
        Opportunity opportunity = loadOpportunity(opportunityId);
        boolean saved = savedItemRepository.existsByUserIdAndItemTypeAndItemId(
            currentUserProvider.getCurrentUserId(), SavedItemType.OPPORTUNITY, opportunityId);
        return toResponse(opportunity, saved);
    }

    @Override
    @Transactional
    public OpportunityResponse create(OpportunityCreateRequest request) {
        Opportunity opportunity = opportunityRepository.save(Opportunity.builder()
            .title(request.title().strip())
            .organization(request.organization())
            .type(request.type())
            .description(request.description())
            .deadline(request.deadline())
            .location(request.location())
            .applyLink(request.applyLink())
            .build());
        return toResponse(opportunity, false);
    }

    @Override
    @Transactional
    public OpportunityResponse update(UUID opportunityId, OpportunityCreateRequest request) {
        Opportunity opportunity = loadOpportunity(opportunityId);
        opportunity.setTitle(request.title().strip());
        opportunity.setOrganization(request.organization());
        opportunity.setType(request.type());
        opportunity.setDescription(request.description());
        opportunity.setDeadline(request.deadline());
        opportunity.setLocation(request.location());
        opportunity.setApplyLink(request.applyLink());
        Opportunity saved = opportunityRepository.save(opportunity);
        return toResponse(saved, isSaved(opportunityId));
    }

    @Override
    @Transactional
    public void delete(UUID opportunityId) {
        Opportunity opportunity = loadOpportunity(opportunityId);
        opportunity.setDeletedAt(Instant.now());
        opportunityRepository.save(opportunity);
    }

    /** Resolves savedByMe for the whole page in one query rather than per row. */
    private PageResponse<OpportunityResponse> hydrate(Page<Opportunity> page) {
        List<Opportunity> opportunities = page.getContent();
        if (opportunities.isEmpty()) {
            return PageResponse.from(page, o -> toResponse(o, false));
        }
        Set<UUID> ids = opportunities.stream().map(Opportunity::getId).collect(Collectors.toSet());
        Set<UUID> saved = new HashSet<>(savedItemRepository.findSavedItemIds(
            currentUserProvider.getCurrentUserId(), SavedItemType.OPPORTUNITY, ids));
        return PageResponse.from(page, o -> toResponse(o, saved.contains(o.getId())));
    }

    private boolean isSaved(UUID opportunityId) {
        return savedItemRepository.existsByUserIdAndItemTypeAndItemId(
            currentUserProvider.getCurrentUserId(), SavedItemType.OPPORTUNITY, opportunityId);
    }

    private Opportunity loadOpportunity(UUID opportunityId) {
        return opportunityRepository.findByIdAndDeletedAtIsNull(opportunityId)
            .orElseThrow(() -> new NotFoundException("Opportunity not found"));
    }

    private OpportunityResponse toResponse(Opportunity opportunity, boolean savedByMe) {
        boolean expired = opportunity.getDeadline() != null && opportunity.getDeadline().isBefore(LocalDate.now());
        return new OpportunityResponse(
            opportunity.getId(),
            opportunity.getTitle(),
            opportunity.getOrganization(),
            opportunity.getType(),
            opportunity.getDescription(),
            opportunity.getDeadline(),
            opportunity.getLocation(),
            opportunity.getApplyLink(),
            savedByMe,
            expired,
            opportunity.getCreatedAt()
        );
    }
}
