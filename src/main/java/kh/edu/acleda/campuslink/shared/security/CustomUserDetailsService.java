package kh.edu.acleda.campuslink.shared.security;

import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import java.util.Collections;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPasswordHash() == null ? "" : user.getPasswordHash(),
            user.getAccountStatus() == AccountStatus.ACTIVE && user.getDeletedAt() == null,
            true,
            true,
            true,
            Collections.singleton(user.getRole().asAuthority()));
    }
}
