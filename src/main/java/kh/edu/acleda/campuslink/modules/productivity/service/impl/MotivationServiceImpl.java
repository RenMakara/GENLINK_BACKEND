package kh.edu.acleda.campuslink.modules.productivity.service.impl;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.MotivationResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.MotivationContent;
import kh.edu.acleda.campuslink.modules.productivity.entity.UserSavedMotivation;
import kh.edu.acleda.campuslink.modules.productivity.repository.MotivationContentRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.UserSavedMotivationRepository;
import kh.edu.acleda.campuslink.modules.productivity.service.MotivationService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.localization.LocalizedTextSelector;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MotivationServiceImpl implements MotivationService {

    private final MotivationContentRepository motivationRepository;
    private final UserSavedMotivationRepository savedRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public MotivationServiceImpl(MotivationContentRepository motivationRepository,
                                 UserSavedMotivationRepository savedRepository,
                                 UserRepository userRepository,
                                 CurrentUserProvider currentUserProvider) {
        this.motivationRepository = motivationRepository;
        this.savedRepository = savedRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MotivationResponse> getAll(String category, Language language) {
        List<MotivationContent> items = category == null
            ? motivationRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()
            : motivationRepository.findByCategoryAndDeletedAtIsNullOrderByCreatedAtDesc(category);
        Set<UUID> saved = savedMotivationIds();
        return items.stream().map(m -> toResponse(m, saved.contains(m.getId()), language)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MotivationResponse getById(UUID id, Language language) {
        MotivationContent motivation = loadMotivation(id);
        return toResponse(motivation, isSavedByCurrentUser(id), language);
    }

    @Override
    @Transactional
    public MotivationResponse save(UUID id, Language language) {
        MotivationContent motivation = loadMotivation(id);
        UUID userId = currentUserProvider.getCurrentUserId();
        if (savedRepository.findByUserIdAndMotivationId(userId, id).isEmpty()) {
            try {
                savedRepository.saveAndFlush(UserSavedMotivation.builder()
                    .user(loadCurrentUser()).motivation(motivation).savedAt(LocalDateTime.now()).build());
                motivationRepository.incrementSavedCount(id);
            } catch (DataIntegrityViolationException alreadySaved) {
                // A concurrent save won the UNIQUE(user, motivation) race; it did the count.
            }
        }
        return toResponse(loadMotivation(id), true, language);
    }

    @Override
    @Transactional
    public MotivationResponse unsave(UUID id, Language language) {
        loadMotivation(id);
        UUID userId = currentUserProvider.getCurrentUserId();
        savedRepository.findByUserIdAndMotivationId(userId, id).ifPresent(saved -> {
            savedRepository.delete(saved);
            motivationRepository.decrementSavedCount(id);
        });
        return toResponse(loadMotivation(id), false, language);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MotivationResponse> getSaved(Language language) {
        return savedRepository.findByUserIdOrderBySavedAtDesc(currentUserProvider.getCurrentUserId()).stream()
            .map(UserSavedMotivation::getMotivation)
            .filter(m -> m != null && m.getDeletedAt() == null)
            .map(m -> toResponse(m, true, language))
            .toList();
    }

    private Set<UUID> savedMotivationIds() {
        Set<UUID> ids = new HashSet<>();
        for (UserSavedMotivation s : savedRepository.findByUserIdOrderBySavedAtDesc(
                currentUserProvider.getCurrentUserId())) {
            if (s.getMotivation() != null) {
                ids.add(s.getMotivation().getId());
            }
        }
        return ids;
    }

    private boolean isSavedByCurrentUser(UUID motivationId) {
        return savedRepository
            .findByUserIdAndMotivationId(currentUserProvider.getCurrentUserId(), motivationId).isPresent();
    }

    private MotivationResponse toResponse(MotivationContent m, boolean saved, Language language) {
        return new MotivationResponse(m.getId(),
            LocalizedTextSelector.select(m.getTitleEn(), m.getTitleKm(), language),
            LocalizedTextSelector.select(m.getContentEn(), m.getContentKm(), language),
            m.getCategory(), m.getSavedCount() == null ? 0 : m.getSavedCount(), saved);
    }

    private MotivationContent loadMotivation(UUID id) {
        return motivationRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("Motivation not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
