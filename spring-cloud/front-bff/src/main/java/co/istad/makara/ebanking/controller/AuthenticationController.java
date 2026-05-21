package co.istad.makara.ebanking.controller;

import co.istad.makara.ebanking.dto.AuthenticationResponse;
import co.istad.makara.ebanking.dto.ProfileResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

//    @GetMapping("/me")
//    public ProfileResponse me(@AuthenticationPrincipal OidcUser principal) {
//        System.out.println(principal.getEmail());
//        System.out.println(principal.getName());
//        System.out.println(principal.getAddress());
//        System.out.println(principal.getSubject());
//        return ProfileResponse.builder()
//                .username(principal.getName())
//                .fullName("ISTAD")
//
//                .build();
//    }

    @GetMapping("/me")
    public Mono<ProfileResponse> me(@AuthenticationPrincipal OidcUser principal) {

        return Mono.just(ProfileResponse.builder()
                .uuid(principal.getAttribute("uuid"))
                .username(principal.getPreferredUsername() != null
                        ? principal.getPreferredUsername()
                        : principal.getName())
                .email(principal.getEmail())
                .givenName(principal.getGivenName())
                .familyName(principal.getFamilyName())
                .dob(principal.getAttribute("dob"))
                .gender(principal.getAttribute("gender"))
                .profileImage(principal.getAttribute("profile_image"))
                .coverImage(principal.getAttribute("cover_image"))
                .phoneNumber(principal.getAttribute("phone_number"))
                .build());
    }


    @GetMapping("/is-authenticated")
    public AuthenticationResponse isAuthenticated(Authentication authentication) {
        return AuthenticationResponse.builder()
                .isAuthenticated(authentication != null)
                .build();
    }

}
