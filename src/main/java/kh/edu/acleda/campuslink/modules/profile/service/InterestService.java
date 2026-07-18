package kh.edu.acleda.campuslink.modules.profile.service;

import kh.edu.acleda.campuslink.modules.profile.dto.request.UserInterestsUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.InterestResponse;
import kh.edu.acleda.campuslink.shared.enums.Language;

import java.util.List;
import java.util.UUID;

public interface InterestService {

    /** Catalogue for the onboarding chooser, names resolved to {@code language}. */
    List<InterestResponse> getCatalog(Language language);

    List<InterestResponse> getMine(Language language);

    List<InterestResponse> getByUserId(UUID userId, Language language);

    /** Replaces the caller's whole selection. */
    List<InterestResponse> replaceMine(UserInterestsUpdateRequest request, Language language);
}
