package kh.edu.acleda.campuslink.modules.profile.service;

import kh.edu.acleda.campuslink.modules.profile.dto.request.SocialLinkUpsertRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.SocialLinkResponse;
import kh.edu.acleda.campuslink.modules.profile.enums.SocialPlatform;

import java.util.List;
import java.util.UUID;

public interface SocialLinkService {

    List<SocialLinkResponse> getMine();

    List<SocialLinkResponse> getByUserId(UUID userId);

    /** Creates or updates the caller's link for the given platform. */
    SocialLinkResponse upsert(SocialLinkUpsertRequest request);

    void delete(SocialPlatform platform);
}
