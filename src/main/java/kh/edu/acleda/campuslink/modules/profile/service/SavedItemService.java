package kh.edu.acleda.campuslink.modules.profile.service;

import kh.edu.acleda.campuslink.modules.profile.dto.request.SaveItemRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.SavedItemResponse;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;

import java.util.List;
import java.util.UUID;

public interface SavedItemService {

    /** @param itemType optional filter; null returns every saved item. */
    List<SavedItemResponse> getMine(SavedItemType itemType);

    /** Idempotent: saving an already-saved item returns the existing row. */
    SavedItemResponse save(SaveItemRequest request);

    void unsave(SavedItemType itemType, UUID itemId);

    boolean isSaved(SavedItemType itemType, UUID itemId);
}
