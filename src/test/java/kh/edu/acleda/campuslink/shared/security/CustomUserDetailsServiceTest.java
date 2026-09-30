package kh.edu.acleda.campuslink.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.Role;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomUserDetailsServiceTest {

    private static final String EMAIL = "user@example.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminAuthorityProvider adminAuthorityProvider;

    private CustomUserDetailsService service() {
        return new CustomUserDetailsService(userRepository, adminAuthorityProvider);
    }

    /** ADM-010 AC#1, and regression test S2 of the five pinned in testing-plan.md. */
    @ParameterizedTest(name = "{0} account is disabled")
    @EnumSource(value = AccountStatus.class, names = {"PENDING", "BANNED", "SUSPENDED", "DELETED"})
    void onlyActiveAccountsAreEnabled(AccountStatus status) {
        stubUser(status, null);

        assertThat(service().loadUserByUsername(EMAIL).isEnabled())
            .as("%s must not be enabled", status)
            .isFalse();
    }

    @Test
    @DisplayName("an ACTIVE account is enabled")
    void activeAccountIsEnabled() {
        stubUser(AccountStatus.ACTIVE, null);

        assertThat(service().loadUserByUsername(EMAIL).isEnabled()).isTrue();
    }

    @Test
    @DisplayName("a soft-deleted ACTIVE account is still disabled")
    void softDeletedAccountIsDisabled() {
        stubUser(AccountStatus.ACTIVE, java.time.Instant.now());

        assertThat(service().loadUserByUsername(EMAIL).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("an ordinary user's authorities are unchanged: role only, no PERM_*")
    void ordinaryUserKeepsOnlyItsRole() {
        stubUser(AccountStatus.ACTIVE, null);
        when(adminAuthorityProvider.authoritiesFor(anyString())).thenReturn(List.of());

        UserDetails details = service().loadUserByUsername(EMAIL);

        assertThat(names(details)).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("a staff account gets its role plus its PERM_* authorities")
    void staffGetsRoleAndPermissions() {
        stubUser(AccountStatus.ACTIVE, null);
        when(adminAuthorityProvider.authoritiesFor(EMAIL)).thenReturn(List.of(
            new SimpleGrantedAuthority("PERM_user:ban"),
            new SimpleGrantedAuthority("PERM_user:read")));

        UserDetails details = service().loadUserByUsername(EMAIL);

        // The role authority is preserved, so existing hasRole('ADMIN') checks are unaffected.
        assertThat(names(details)).contains("ROLE_USER", "PERM_user:ban", "PERM_user:read");
    }

    private void stubUser(AccountStatus status, java.time.Instant deletedAt) {
        User user = new User();
        user.setEmail(EMAIL);
        user.setPasswordHash("hash");
        user.setAccountStatus(status);
        user.setDeletedAt(deletedAt);
        user.setRole(Role.USER);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private List<String> names(UserDetails details) {
        return details.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
