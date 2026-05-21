package istad.makara.identity.security;

import istad.makara.identity.domain.Role;
import istad.makara.identity.domain.User;
import istad.makara.identity.features.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // Load user from database
        User loggedInUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

//        // Build userDetails object
//        String[] roles = loggedInUser.getRoles().stream()
//                .map(Role::getName)
//                .toArray(String[]::new);
//
//
//        List<GrantedAuthority> authorities = new ArrayList<>();
//        loggedInUser.getRoles().forEach(role ->{
//            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
//            role.getPermissions().forEach(permission -> {
//                authorities.add(new SimpleGrantedAuthority(permission.getName()));
//            });
//        });
//
//        UserDetails userSecurity = org.springframework.security.core.userdetails.User.builder()
//                .username(loggedInUser.getUsername())
//                .password(loggedInUser.getPassword())
////                .roles(roles)
//                .authorities(authorities)
//                .build();

        CustomUserDetails customUserDetails = new CustomUserDetails(loggedInUser);
        log.info("UserDetailsServiceImpl customUserDetails = {}", customUserDetails);

        return customUserDetails;
    }
}
