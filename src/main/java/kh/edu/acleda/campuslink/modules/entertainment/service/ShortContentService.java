package kh.edu.acleda.campuslink.modules.entertainment.service;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.request.ShortContentCreateRequest;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.ShortContentResponse;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface ShortContentService {

    /** Create a short after the shared media upload; the creator is the current user. */
    ShortContentResponse create(ShortContentCreateRequest request, Language language);

    /** Soft-delete a short owned by the current user. */
    void delete(UUID id);

    /** The feed, optionally by category, with the user's liked/saved flags. */
    PageResponse<ShortContentResponse> getFeed(String category, Pageable pageable, Language language);

    ShortContentResponse getById(UUID id, Language language);

    ShortContentResponse like(UUID id, Language language);

    ShortContentResponse unlike(UUID id, Language language);

    ShortContentResponse save(UUID id, Language language);

    ShortContentResponse unsave(UUID id, Language language);

    ShortContentResponse share(UUID id, Language language);

    /** The current user's saved shorts, newest first. */
    List<ShortContentResponse> getSaved(Language language);
}
