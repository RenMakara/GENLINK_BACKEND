package kh.edu.acleda.campuslink.modules.user;

import kh.edu.acleda.campuslink.modules.auth.repository.SocialAccountRepository;
import kh.edu.acleda.campuslink.modules.media.MediaCleanupService;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Scrubs personal data from accounts whose deletion grace period has expired. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountAnonymizationService {

    /** How long a soft-deleted account stays recoverable. */
    public static final int GRACE_PERIOD_DAYS = 30;

    /** Accounts scrubbed per run. */
    private static final int BATCH_LIMIT = 500;

    /** RFC 2606 reserves {@code .invalid} as a TLD that can never resolve. */
    private static final String ANONYMOUS_EMAIL_DOMAIN = "@genlinkkh.invalid";

    private static final String ANONYMOUS_FULL_NAME = "Deleted User";

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final MediaCleanupService mediaCleanupService;

    /** Anonymises every account past its grace period, up to {@link #BATCH_LIMIT}. */
    @Transactional
    public int anonymizeExpiredAccounts() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(GRACE_PERIOD_DAYS));

        List<User> expired = userRepository.findByAccountStatusAndDeletedAtBeforeOrderByDeletedAtAsc(
            AccountStatus.DELETED, cutoff, PageRequest.of(0, BATCH_LIMIT));

        if (expired.isEmpty()) {
            return 0;
        }

        expired.forEach(this::anonymize);
        userRepository.saveAll(expired);

        if (expired.size() == BATCH_LIMIT) {
            log.warn("Anonymised {} accounts (batch limit reached) — more remain for the next run",
                expired.size());
        } else {
            log.info("Anonymised {} account(s) deleted before {}", expired.size(), cutoff);
        }
        return expired.size();
    }

    /** Overwrites every field that identifies a person, and frees the unique columns. */
    private void anonymize(User user) {
        String token = user.getId().toString().replace("-", "").substring(0, 12);

        // Unique columns: rewritten rather than nulled, because email and username are NOT NULL.
        user.setEmail("deleted-" + token + ANONYMOUS_EMAIL_DOMAIN);
        user.setUsername("deleted_" + token);
        user.setPhoneNumber(null);

        // Profile.
        user.setFullName(ANONYMOUS_FULL_NAME);
        user.setBio(null);
        user.setAvatarUrl(null);
        user.setUniversityName(null);
        user.setLocation(null);
        user.setDateOfBirth(null);
        user.setGender(null);

        // Credentials. Clearing these means the account cannot be authenticated into
        // even if the status check were ever bypassed.
        user.setPasswordHash(null);
        user.setGoogleId(null);
        user.setAuthProvider(AuthProvider.LOCAL);
        // A scrubbed row is no longer restorable.
        user.setStatusBeforeDeletion(null);

        // Access tokens are stateless and validated against this version, so bumping it
        // invalidates anything still in flight.
        user.incrementTokenVersion();

        socialAccountRepository.deleteByUser(user);

        // Their uploads are personal data too.
        mediaCleanupService.softDeleteAllOwnedBy(user.getId());

        log.debug("Anonymised account {}", user.getId());
    }
}
