package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.DailyChallenge;
import kh.edu.acleda.campuslink.modules.entertainment.entity.UserChallenge;
import kh.edu.acleda.campuslink.modules.entertainment.enums.ChallengeStatus;
import kh.edu.acleda.campuslink.modules.entertainment.service.ChallengeRewardNotifier;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Builds the stable, navigable notification for a claimed challenge reward. */
@Slf4j
@Service
public class ChallengeRewardNotifierImpl implements ChallengeRewardNotifier {

    private static final String TEMPLATE_KEY = "ENTERTAINMENT_CHALLENGE_COMPLETE";
    private static final String NOTIFICATION_TYPE = "ENTERTAINMENT_CHALLENGE";

    private final NotificationEmitter notificationEmitter;

    public ChallengeRewardNotifierImpl(NotificationEmitter notificationEmitter) {
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    public void notifyForClaimedChallenge(UserChallenge userChallenge, String challengeName) {
        if (userChallenge == null || userChallenge.getStatus() != ChallengeStatus.CLAIMED
            || challengeName == null || challengeName.isBlank()) {
            return;
        }

        DailyChallenge challenge = userChallenge.getChallenge();
        if (challenge == null || challenge.getId() == null || challenge.getXpReward() == null
            || challenge.getXpReward() <= 0 || userChallenge.getUser() == null
            || userChallenge.getUser().getId() == null || userChallenge.getId() == null) {
            log.warn("Skipping challenge reward notification because details are incomplete: userChallenge={}",
                userChallenge.getId());
            return;
        }

        UUID challengeId = challenge.getId();
        notificationEmitter.emit(new NotificationRequest(
            userChallenge.getUser().getId(),
            TEMPLATE_KEY,
            NOTIFICATION_TYPE,
            Map.of(
                "challengeName", challengeName,
                "rewardXp", Integer.toString(challenge.getXpReward())),
            "CHALLENGE",
            challengeId,
            "challenge-reward:" + challengeId));
    }
}
