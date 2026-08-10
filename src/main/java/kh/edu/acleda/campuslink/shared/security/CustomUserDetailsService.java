package kh.edu.acleda.campuslink.shared.security;

import java.util.ArrayList;
import java.util.List;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final AdminAuthorityProvider adminAuthorityProvider;

    public CustomUserDetailsService(UserRepository userRepository,
                                    AdminAuthorityProvider adminAuthorityProvider) {
        this.userRepository = userRepository;
        this.adminAuthorityProvider = adminAuthorityProvider;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        return new AuthenticatedUser(
            user.getEmail(),
            user.getPasswordHash() == null ? "" : user.getPasswordHash(),
            // An allow-list, not a deny-list. Anything that is not ACTIVE - PENDING, BANNED,
            // SUSPENDED, DELETED - is disabled, so a new account status is locked out by
            // default rather than by remembering to add it here.
            user.getAccountStatus() == AccountStatus.ACTIVE && user.getDeletedAt() == null,
            user.getTokenVersion(),
            authoritiesFor(user));
    }

    /**
     * The account's {@code ROLE_*} authority, plus {@code PERM_*} authorities when it is a
     * staff account.
     *
     * <p>The role authority is unchanged, so nothing that already relies on
     * {@code hasRole('ADMIN')} shifts. Permissions are additive: an ordinary user gets exactly
     * what they got before, and the admin lookup returns an empty list for them off a cache.
     *
     * <p>Resolved here rather than read from the JWT deliberately. This method already runs on
     * every authenticated request, so it costs nothing extra to ask - and unlike a token
     * claim, a revoked permission stops working on the very next request instead of when the
     * token expires.
     */
    private List<GrantedAuthority> authoritiesFor(User user) {
        List<GrantedAuthority> adminAuthorities = adminAuthorityProvider.authoritiesFor(user.getEmail());
        if (adminAuthorities.isEmpty()) {
            return List.of(user.getRole().asAuthority());
        }
        List<GrantedAuthority> authorities = new ArrayList<>(adminAuthorities.size() + 1);
        authorities.add(user.getRole().asAuthority());
        authorities.addAll(adminAuthorities);
        return List.copyOf(authorities);
    }
}
