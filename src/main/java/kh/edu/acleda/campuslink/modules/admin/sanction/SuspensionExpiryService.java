package kh.edu.acleda.campuslink.modules.admin.sanction;

import java.time.Instant;
import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.sanction.entity.UserSanction;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionType;
import kh.edu.acleda.campuslink.modules.admin.sanction.repository.UserSanctionRepository;
import kh.edu.acleda.campuslink.modules.auth.RefreshTokenService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies the state transition for expired suspensions (ADM-079). */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuspensionExpiryService {

    private static final String AUTOMATIC_LIFT_REASON = "Suspension expired automatically";

    private final UserSanctionRepository userSanctionRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    /** Lifts every expired, still-open suspension visible at {@code now}. */
    @Transactional
    public int expireSuspensions(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }

        List<UserSanction> expired = userSanctionRepository.findExpired(now);
        int lifted = 0;
        for (UserSanction candidate : expired) {
            if (!isExpiredOpenSuspension(candidate, now)) {
                continue;
            }

            User targetReference = candidate.getUser();
            if (targetReference == null || targetReference.getId() == null) {
                log.warn("Skipping expired suspension {} without a valid target user",
                    candidate.getId());
                continue;
            }

            User user = userRepository.findByIdForUpdate(targetReference.getId()).orElse(null);
            if (user == null) {
                log.warn("Skipping expired suspension {} because target user {} was not found",
                    candidate.getId(), targetReference.getId());
                continue;
            }

            // The user lock serialises this transition with ban/unban/suspend.
            UserSanction sanction = candidate;
            if (candidate.getId() != null) {
                sanction = userSanctionRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            }
            if (!isExpiredOpenSuspension(sanction, now)) {
                continue;
            }

            AccountStatus previousStatus = sanction.getPreviousStatus();
            if (previousStatus == null || previousStatus == AccountStatus.DELETED
                || previousStatus == AccountStatus.BANNED
                || previousStatus == AccountStatus.SUSPENDED) {
                throw new IllegalStateException(
                    "Expired suspension has an invalid previous account status: "
                        + previousStatus);
            }

            // A later ban or another administrative transition wins. The expired suspension is
            // still closed, but it must never downgrade that newer state.
            if (user.getAccountStatus() == AccountStatus.SUSPENDED) {
                user.setAccountStatus(previousStatus);
                user.incrementTokenVersion();
                refreshTokenService.revoke(user);
                userRepository.save(user);
            }

            sanction.setLiftedAt(now);
            sanction.setLiftReason(AUTOMATIC_LIFT_REASON);
            userSanctionRepository.save(sanction);
            lifted++;
        }

        if (lifted > 0) {
            log.info("Suspension expiry run lifted {} suspension(s)", lifted);
        }
        return lifted;
    }

    private boolean isExpiredOpenSuspension(UserSanction sanction, Instant now) {
        return sanction != null
            && sanction.getType() == SanctionType.SUSPENSION
            && sanction.getLiftedAt() == null
            && sanction.getDeletedAt() == null
            && sanction.getExpiresAt() != null
            && sanction.getExpiresAt().isBefore(now);
    }
}
