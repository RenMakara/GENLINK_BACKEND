package kh.edu.acleda.campuslink.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
    @NotBlank(message = "ID Token is required")
    String idToken
) {}
