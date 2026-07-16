package kh.edu.acleda.campuslink.modules.profile.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.dto.request.UserInterestsUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.InterestResponse;
import kh.edu.acleda.campuslink.modules.profile.entity.Interest;
import kh.edu.acleda.campuslink.modules.profile.entity.UserInterest;
import kh.edu.acleda.campuslink.modules.profile.repository.InterestRepository;
import kh.edu.acleda.campuslink.modules.profile.repository.UserInterestRepository;
import kh.edu.acleda.campuslink.modules.profile.service.InterestService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.localization.LocalizedTextSelector;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterestServiceImpl implements InterestService {

    private final InterestRepository interestRepository;
    private final UserInterestRepository userInterestRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public InterestServiceImpl(InterestRepository interestRepository,
                               UserInterestRepository userInterestRepository,
                               UserRepository userRepository,
                               CurrentUserProvider currentUserProvider) {
        this.interestRepository = interestRepository;
        this.userInterestRepository = userInterestRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterestResponse> getCatalog(Language language) {
        return interestRepository.findByIsActiveTrueOrderByNameEnAsc()
            .stream()
            .map(interest -> toResponse(interest, language))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterestResponse> getMine(Language language) {
        return getByUserId(currentUserProvider.getCurrentUserId(), language);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterestResponse> getByUserId(UUID userId, Language language) {
        return userInterestRepository.findByUserId(userId)
            .stream()
            .map(userInterest -> toResponse(userInterest.getInterest(), language))
            .toList();
    }

    @Override
    @Transactional
    public List<InterestResponse> replaceMine(UserInterestsUpdateRequest request, Language language) {
        UUID userId = currentUserProvider.getCurrentUserId();
        Set<UUID> requestedIds = new HashSet<>(request.interestIds());

        List<Interest> interests = requestedIds.isEmpty()
            ? List.of()
            : interestRepository.findByIdInAndIsActiveTrue(requestedIds);

        // Reject unknown or inactive ids rather than silently dropping them, so the
        // client cannot believe it saved a selection it did not.
        if (interests.size() != requestedIds.size()) {
            throw new ApiException("One or more interests are unknown or inactive", "VALIDATION_ERROR");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));

        // Replace wholesale: the onboarding step submits the full selection, and the
        // unique (user_id, interest_id) constraint makes incremental merging pointless.
        userInterestRepository.deleteByUserId(userId);
        userInterestRepository.flush();

        List<UserInterest> saved = userInterestRepository.saveAll(
            interests.stream()
                .map(interest -> UserInterest.builder().user(user).interest(interest).build())
                .toList());

        return saved.stream()
            .map(userInterest -> toResponse(userInterest.getInterest(), language))
            .toList();
    }

    private InterestResponse toResponse(Interest interest, Language language) {
        return new InterestResponse(
            interest.getId(),
            interest.getCode(),
            LocalizedTextSelector.select(interest.getNameEn(), interest.getNameKm(), language),
            interest.getCategory()
        );
    }
}
