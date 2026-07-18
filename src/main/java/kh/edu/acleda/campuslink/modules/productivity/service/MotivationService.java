package kh.edu.acleda.campuslink.modules.productivity.service;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.MotivationResponse;
import kh.edu.acleda.campuslink.shared.enums.Language;

public interface MotivationService {

    /** Motivation content, optionally filtered by category, each with the user's saved flag. */
    List<MotivationResponse> getAll(String category, Language language);

    MotivationResponse getById(UUID id, Language language);

    /** Idempotent save; bumps saved_count only on the first save. */
    MotivationResponse save(UUID id, Language language);

    /** Idempotent unsave; drops saved_count only when a save is actually removed. */
    MotivationResponse unsave(UUID id, Language language);

    /** The current user's saved motivation, newest first. */
    List<MotivationResponse> getSaved(Language language);
}
