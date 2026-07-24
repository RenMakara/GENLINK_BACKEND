package kh.edu.acleda.campuslink.modules.user;

import kh.edu.acleda.campuslink.modules.auth.repository.SocialAccountRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAnonymizationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SocialAccountRepository socialAccountRepository;

    @InjectMocks
    private AccountAnonymizationService accountAnonymizationService;

    @Test
    void scrubsIdentifyingFieldsAndReleasesTheEmailAndUsername() {
        User user = expiredAccount();

        when(userRepository.findByAccountStatusAndDeletedAtBeforeOrderByDeletedAtAsc(
            eq(AccountStatus.DELETED), any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(user));

        int count = accountAnonymizationService.anonymizeExpiredAccounts();

        assertThat(count).isEqualTo(1);
        // The original address must no longer occupy the UNIQUE index, which is what
        // lets the person (or anyone) register it again.
        assertThat(user.getEmail())
            .isNotEqualTo("someone@example.com")
            .endsWith("@genlinkkh.invalid");
        assertThat(user.getUsername()).startsWith("deleted_").isNotEqualTo("someone");
        assertThat(user.getPhoneNumber()).isNull();
        // Personal data gone, but the row survives so foreign keys still resolve.
        assertThat(user.getFullName()).isEqualTo("Deleted User");
        assertThat(user.getBio()).isNull();
        assertThat(user.getAvatarUrl()).isNull();
        assertThat(user.getUniversityName()).isNull();
        assertThat(user.getDateOfBirth()).isNull();
        // Credentials cleared, so the account cannot be authenticated into.
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getGoogleId()).isNull();
        assertThat(user.getTokenVersion()).isEqualTo(1);

        verify(socialAccountRepository).deleteByUser(user);
        verify(userRepository).saveAll(anyList());
    }

    @Test
    void selectsOnlyAccountsDeletedBeforeTheGracePeriodCutoff() {
        when(userRepository.findByAccountStatusAndDeletedAtBeforeOrderByDeletedAtAsc(
            eq(AccountStatus.DELETED), any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of());

        accountAnonymizationService.anonymizeExpiredAccounts();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository).findByAccountStatusAndDeletedAtBeforeOrderByDeletedAtAsc(
            eq(AccountStatus.DELETED), cutoff.capture(), any(Pageable.class));
        assertThat(cutoff.getValue()).isCloseTo(
            Instant.now().minus(AccountAnonymizationService.GRACE_PERIOD_DAYS, ChronoUnit.DAYS),
            within(1, ChronoUnit.MINUTES));
    }

    @Test
    void writesNothingWhenNoAccountHasExpired() {
        when(userRepository.findByAccountStatusAndDeletedAtBeforeOrderByDeletedAtAsc(
            eq(AccountStatus.DELETED), any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of());

        assertThat(accountAnonymizationService.anonymizeExpiredAccounts()).isZero();

        verify(userRepository, never()).saveAll(anyList());
        verify(socialAccountRepository, never()).deleteByUser(any(User.class));
    }

    @Test
    void isIdempotentSoASecondPassCannotProduceADifferentIdentity() {
        User user = expiredAccount();
        when(userRepository.findByAccountStatusAndDeletedAtBeforeOrderByDeletedAtAsc(
            eq(AccountStatus.DELETED), any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(user));

        accountAnonymizationService.anonymizeExpiredAccounts();
        String emailAfterFirstPass = user.getEmail();
        String usernameAfterFirstPass = user.getUsername();

        accountAnonymizationService.anonymizeExpiredAccounts();

        // Values derive from the user id, so a repeat run rewrites the same strings
        // instead of appending and eventually overflowing the column.
        assertThat(user.getEmail()).isEqualTo(emailAfterFirstPass);
        assertThat(user.getUsername()).isEqualTo(usernameAfterFirstPass);
    }

    /** A soft-deleted account well past the grace period, with data worth scrubbing. */
    private User expiredAccount() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setEmail("someone@example.com");
        user.setUsername("someone");
        user.setPhoneNumber("012345678");
        user.setFullName("Some One");
        user.setBio("Third year at ACLEDA");
        user.setAvatarUrl("https://cdn.example.com/a.png");
        user.setUniversityName("ACLEDA University");
        user.setDateOfBirth(java.time.LocalDate.of(2003, 4, 12));
        user.setPasswordHash("hashed");
        user.setGoogleId("google-sub-123");
        user.setAccountStatus(AccountStatus.DELETED);
        user.setDeletedAt(Instant.now().minus(AccountAnonymizationService.GRACE_PERIOD_DAYS + 10,
            ChronoUnit.DAYS));
        return user;
    }
}
