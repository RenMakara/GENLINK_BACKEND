package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.ChallengeResponse;
import kh.edu.acleda.campuslink.modules.entertainment.entity.DailyChallenge;
import kh.edu.acleda.campuslink.modules.entertainment.entity.UserChallenge;
import kh.edu.acleda.campuslink.modules.entertainment.enums.ChallengeStatus;
import kh.edu.acleda.campuslink.modules.entertainment.repository.DailyChallengeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.UserChallengeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.ChallengeRewardNotifier;
import kh.edu.acleda.campuslink.modules.entertainment.service.ChallengeService;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
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
public class ChallengeServiceImpl implements ChallengeService {

    private static final String XP_SOURCE = "CHALLENGE";

    private final DailyChallengeRepository challengeRepository;
    private final UserChallengeRepository userChallengeRepository;
    private final RewardService rewardService;
    private final ChallengeRewardNotifier challengeRewardNotifier;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ChallengeActivityVerifier activityVerifier;

    public ChallengeServiceImpl(DailyChallengeRepository challengeRepository,
                                UserChallengeRepository userChallengeRepository,
                                RewardService rewardService,
                                ChallengeRewardNotifier challengeRewardNotifier,
                                UserRepository userRepository,
                                CurrentUserProvider currentUserProvider,
                                ChallengeActivityVerifier activityVerifier) {
        this.challengeRepository = challengeRepository;
        this.userChallengeRepository = userChallengeRepository;
        this.rewardService = rewardService;
        this.challengeRewardNotifier = challengeRewardNotifier;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.activityVerifier = activityVerifier;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChallengeResponse> getActive(Language language) {
        LocalDateTime now = LocalDateTime.now();
        UUID userId = currentUserProvider.getCurrentUserId();
        return challengeRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
            .filter(c -> c.getDeadline() == null || c.getDeadline().isAfter(now))
            .map(c -> toResponse(c, userChallengeFor(userId, c.getId()), language))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChallengeResponse> getMine(Language language) {
        return userChallengeRepository.findByUserIdOrderByCreatedAtDesc(currentUserProvider.getCurrentUserId())
            .stream()
            .filter(uc -> uc.getChallenge() != null && uc.getChallenge().getDeletedAt() == null)
            .map(uc -> toResponse(uc.getChallenge(), uc, language))
            .toList();
    }

    @Override
    @Transactional
    public ChallengeResponse join(UUID challengeId, Language language) {
        DailyChallenge challenge = loadChallenge(challengeId);
        UserChallenge uc = userChallengeFor(currentUserProvider.getCurrentUserId(), challengeId);
        if (uc == null) {
            uc = userChallengeRepository.save(UserChallenge.builder()
                .challenge(challenge).user(loadCurrentUser())
                .status(ChallengeStatus.IN_PROGRESS).progress(0).build());
        }
        return toResponse(challenge, uc, language);
    }

    @Override
    @Transactional
    public ChallengeResponse complete(UUID challengeId, Language language) {
        UserChallenge uc = loadJoinedForUpdate(challengeId);
        // Idempotent: only the IN_PROGRESS -> COMPLETED transition does anything.
        if (uc.getStatus() == ChallengeStatus.IN_PROGRESS) {
            if (!activityVerifier.hasQualifyingActivity(
                    uc.getChallenge().getChallengeType(), currentUserProvider.getCurrentUserId(), uc.getCreatedAt())) {
                throw new ApiException("Complete the related activity before completing this challenge",
                    "CHALLENGE_ACTIVITY_NOT_FOUND");
            }
            uc.setStatus(ChallengeStatus.COMPLETED);
            uc.setCompletedAt(LocalDateTime.now());
            uc.setProgress(100);
            userChallengeRepository.save(uc);
        }
        return toResponse(uc.getChallenge(), uc, language);
    }

    @Override
    @Transactional
    public ChallengeResponse claim(UUID challengeId, Language language) {
        UserChallenge uc = loadJoinedForUpdate(challengeId);
        if (uc.getStatus() == ChallengeStatus.CLAIMED) {
            throw new ApiException("Reward already claimed", "CHALLENGE_ALREADY_CLAIMED");
        }
        if (uc.getStatus() != ChallengeStatus.COMPLETED) {
            throw new ApiException("Complete the challenge before claiming", "CHALLENGE_NOT_COMPLETED");
        }
        rewardService.award(loadCurrentUser(), XP_SOURCE, challengeId, uc.getChallenge().getXpReward());
        uc.setStatus(ChallengeStatus.CLAIMED);
        uc.setClaimedAt(LocalDateTime.now());
        userChallengeRepository.save(uc);
        ChallengeResponse response = toResponse(uc.getChallenge(), uc, language);
        challengeRewardNotifier.notifyForClaimedChallenge(uc, response.title());
        return response;
    }

    private UserChallenge userChallengeFor(UUID userId, UUID challengeId) {
        return userChallengeRepository.findByUserIdAndChallengeId(userId, challengeId).orElse(null);
    }

    private ChallengeResponse toResponse(DailyChallenge c, UserChallenge uc, Language language) {
        ChallengeStatus status = uc == null ? ChallengeStatus.NOT_STARTED : uc.getStatus();
        int progress = uc == null || uc.getProgress() == null ? 0 : uc.getProgress();
        return new ChallengeResponse(c.getId(),
            LocalizedTextSelector.select(c.getTitleEn(), c.getTitleKm(), language),
            LocalizedTextSelector.select(c.getDescriptionEn(), c.getDescriptionKm(), language),
            c.getChallengeType(), c.getXpReward(), c.getDeadline(), status, progress);
    }

    private DailyChallenge loadChallenge(UUID id) {
        return challengeRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("Challenge not found"));
    }

    private UserChallenge loadJoined(UUID challengeId) {
        return userChallengeRepository
            .findByUserIdAndChallengeId(currentUserProvider.getCurrentUserId(), challengeId)
            .orElseThrow(() -> new ApiException("Join the challenge first", "CHALLENGE_NOT_JOINED"));
    }

    private UserChallenge loadJoinedForUpdate(UUID challengeId) {
        return userChallengeRepository
            .findByUserIdAndChallengeIdForUpdate(currentUserProvider.getCurrentUserId(), challengeId)
            .orElseThrow(() -> new ApiException("Join the challenge first", "CHALLENGE_NOT_JOINED"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
