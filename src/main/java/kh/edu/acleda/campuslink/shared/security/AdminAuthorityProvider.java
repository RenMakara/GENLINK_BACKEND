package kh.edu.acleda.campuslink.shared.security;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;

/** Supplies the {@code PERM_*} authorities for a staff account. */
public interface AdminAuthorityProvider {

    /** @param email the account's login address. */
    List<GrantedAuthority> authoritiesFor(String email);
}
