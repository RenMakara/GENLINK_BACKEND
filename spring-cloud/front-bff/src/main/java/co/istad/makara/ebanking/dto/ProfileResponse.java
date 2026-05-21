package co.istad.makara.ebanking.dto;


import lombok.Builder;

import java.time.LocalDate;
import java.util.Set;

@Builder
public record ProfileResponse(
        String uuid,
        String username,
        String email,
        String givenName,
        String familyName,
        String phoneNumber,
        String gender,
        LocalDate dob,
        String profileImage,
        String coverImage,
        Set<String> roles,
        Set<String> permissions

) {
}
