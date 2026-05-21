package kh.edu.acleda.campuslink.modules.user.entity;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

public enum Role {
    USER,
    ADMIN;

    public SimpleGrantedAuthority asAuthority() {
        return new SimpleGrantedAuthority("ROLE_" + name());
    }
}

