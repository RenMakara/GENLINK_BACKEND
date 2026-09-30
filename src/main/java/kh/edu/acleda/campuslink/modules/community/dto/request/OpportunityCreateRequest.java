package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** @param type free text (INTERNSHIP, SCHOLARSHIP, HACKATHON, JOB, VOLUNTEER, ...). */
public record OpportunityCreateRequest(
    @NotBlank @Size(max = 180) String title,
    @Size(max = 180) String organization,
    @Size(max = 80) String type,
    String description,
    LocalDate deadline,
    @Size(max = 120) String location,
    String applyLink
) {
}
