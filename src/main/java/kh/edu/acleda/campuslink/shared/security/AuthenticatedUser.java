package kh.edu.acleda.campuslink.shared.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * UserDetails carrying the account's current token version.
 *
 * <p>The JWT filter already loads the user on every request, so exposing the version
 * here lets it reject superseded access tokens without a second query.
 */
public class AuthenticatedUser extends User {

    private final int tokenVersion;

    public AuthenticatedUser(String username,
                             String password,
                             boolean enabled,
                             int tokenVersion,
                             Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.tokenVersion = tokenVersion;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }
}
