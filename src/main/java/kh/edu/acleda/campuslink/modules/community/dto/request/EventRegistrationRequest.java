package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The attendee details collected by the mobile full-registration form. */
public record EventRegistrationRequest(
    @NotBlank @Size(max = 180) String fullName,
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 32) String phoneNumber,
    @NotBlank @Size(max = 180) String university,
    @NotBlank @Size(max = 180) String faculty,
    @NotBlank @Size(max = 80) String studyYear,
    @AssertTrue(message = "You must accept the event terms") boolean termsAccepted
) {
}
