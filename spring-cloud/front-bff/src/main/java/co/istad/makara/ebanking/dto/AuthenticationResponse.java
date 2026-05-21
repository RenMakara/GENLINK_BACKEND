package co.istad.makara.ebanking.dto;


import lombok.Builder;

@Builder
public record AuthenticationResponse(
        boolean isAuthenticated
) {
}
