package kh.edu.acleda.campuslink.modules.entertainment.service;

import java.util.List;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.BadgeResponse;
import kh.edu.acleda.campuslink.shared.enums.Language;

public interface BadgeService {

    /** The badge catalog, each flagged with whether the current user has earned it. */
    List<BadgeResponse> getAll(Language language);

    /** The current user's unlocked badges, newest first. */
    List<BadgeResponse> getMine(Language language);
}
