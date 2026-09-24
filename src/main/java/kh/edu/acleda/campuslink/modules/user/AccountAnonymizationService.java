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

/**
 * Scrubs personal data from accounts whose deletion grace period has expired.
 *
 * <p>Deletion here is a two-stage process. {@code deleteCurrentAccount} only flips the
 * status to {@link AccountStatus#DELETED} and stamps {@code deletedAt}, which locks the
 * account but keeps every field intact — so a user who changes their mind can register
 * again with the same email and get their account back. This class is the second stage:
 * once {@link #GRACE_PERIOD_DAYS} have passed, the personal fields are overwritten for
 * good and the email, username and phone are released for anyone to use.
 *
 * <p>The row itself is never deleted. 49 foreign keys point at {@code users} and 46 of
 * them have no {@code ON DELETE} clause, so a hard delete would be rejected by Postgres
 * for any account with a single post, habit or OTP behind it. Keeping the row also keeps
 * other people's content coherent: replies to a departed user still resolve, and simply
 * read as "Deleted User".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountAnonymizationService {

    /**
     * How long a soft-deleted account stays recoverable. Long enough to cover the
     * "I deleted it by accident" case, short enough that data is not kept forever.
     */
    public static final int GRACE_PERIOD_DAYS = 30;

    /**
     * Accounts scrubbed per run. Bounds both the transaction and the memory used; a
     * backlog larger than this simply finishes on the following day.
     */
    private static final int BATCH_LIMIT = 500;

    /**
     * RFC 2606 reserves {@code .invalid} as a TLD that can never resolve, so a scrubbed
     * address cannot receive mail even if some future code path tries to send to it.
     */
    private static final String ANONYMOUS_EMAIL_DOMAIN = "@genlinkkh.invalid";

    private static final String ANONYMOUS_FULL_NAME = "Deleted User";

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final MediaCleanupService mediaCleanupService;

    /**
     * Anonymises every account past its grace period, up to {@link #BATCH_LIMIT}.
     *
     * @return how many accounts were scrubbed
     */
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

    /**
     * Overwrites every field that identifies a person, and frees the unique columns.
     *
     * <p>The user id is the source of the replacement values, which guarantees they are
     * unique without a lookup and keeps the operation idempotent — running it twice
     * produces the same row rather than a second collision.
     */
    private void anonymize(User user) {
        String token = user.getId().toString().replace("-", "").substring(0, 12);

        // Unique columns: rewritten rather than nulled, because email and username are
        // NOT NULL. This is what lets the original address be registered again.
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
        // A scrubbed row is no longer restorable. Clearing the captured lifecycle state gives
        // the admin restore path an unambiguous USER_ANONYMISED signal, including for rows that
        // were deleted before the current recovery implementation existed.
        user.setStatusBeforeDeletion(null);

        // Access tokens are stateless and validated against this version, so bumping it
        // invalidates anything still in flight.
        user.incrementTokenVersion();

        socialAccountRepository.deleteByUser(user);

        // Their uploads are personal data too. Soft-deleting starts the media
        // module's own grace period, so MediaCleanupJob purges the bytes rather
        // than leaving them in the bucket after the person is gone.
        mediaCleanupService.softDeleteAllOwnedBy(user.getId());

        log.debug("Anonymised account {}", user.getId());
    }
}
