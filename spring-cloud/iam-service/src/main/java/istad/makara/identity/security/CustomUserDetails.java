package istad.makara.identity.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import istad.makara.identity.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String uuid;
    private final String username;
    private final String email;
    private final String password;
    private final String familyName;
    private final String givenName;
    private final String phoneNumber;
    private final String gender;
    private final LocalDate dob;
    private final String profileImage;
    private final String coverImage;
    private final Boolean accountNonExpired;
    private final Boolean accountNonLocked;
    private final Boolean credentialsNonExpired;
    private final Boolean enabled;

    // Constructor for creating from User entity (used in UserDetailsService)
    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.uuid = user.getUuid();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.familyName = user.getFamilyName();
        this.givenName = user.getGivenName();
        this.phoneNumber = user.getPhoneNumber();
        this.gender = user.getGender();
        this.dob = user.getDob();
        this.profileImage = user.getProfileImage();
        this.coverImage = user.getCoverImage();
        this.accountNonExpired = user.getAccountNonExpired();
        this.accountNonLocked = user.getAccountNonLocked();
        this.credentialsNonExpired = user.getCredentialsNonExpired();
        this.enabled = user.getIsEnabled();

    }


    // Jackson deserialization constructor (for Redis/Session deserialization)
    @JsonCreator
    public CustomUserDetails(
            @JsonProperty("id") Long id,
            @JsonProperty("uuid") String uuid,
            @JsonProperty("username") String username,
            @JsonProperty("email") String email,
            @JsonProperty("password") String password,
            @JsonProperty("familyName") String familyName,
            @JsonProperty("givenName") String givenName,
            @JsonProperty("phoneNumber") String phoneNumber,
            @JsonProperty("gender") String gender,
            @JsonProperty("dob") LocalDate dob,
            @JsonProperty("profileImage") String profileImage,
            @JsonProperty("coverImage") String coverImage,
            @JsonProperty("accountNonExpired") Boolean accountNonExpired,
            @JsonProperty("accountNonLocked") Boolean accountNonLocked,
            @JsonProperty("credentialsNonExpired") Boolean credentialsNonExpired,
            @JsonProperty("enabled") Boolean enabled) {
        this.id = id;
        this.uuid = uuid;
        this.username = username;
        this.email = email;
        this.password = password;
        this.familyName = familyName;
        this.givenName = givenName;
        this.phoneNumber = phoneNumber;
        this.gender = gender;
        this.dob = dob;
        this.profileImage = profileImage;
        this.coverImage = coverImage;
        this.accountNonExpired = accountNonExpired;
        this.accountNonLocked = accountNonLocked;
        this.credentialsNonExpired = credentialsNonExpired;
        this.enabled = enabled;

    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired != null && accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked != null && accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired != null && credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    // Convenience methods for full name
    public String getFullName() {
        return givenName + " " + familyName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomUserDetails that = (CustomUserDetails) o;
        return Objects.equals(getUsername(), that.getUsername()); // Compare based on a unique field
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUsername()); /// Generate hashCode based on the same field
    }
}