package kh.edu.acleda.campuslink.modules.entertainment.service;

import kh.edu.acleda.campuslink.modules.entertainment.entity.UserChallenge;

/** Emits an in-app notification after a challenge reward is successfully claimed. */
public interface ChallengeRewardNotifier {

    void notifyForClaimedChallenge(UserChallenge userChallenge, String challengeName);
}
