package kh.edu.acleda.campuslink.modules.profile.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import kh.edu.acleda.campuslink.modules.profile.dto.request.SaveItemRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.SavedItemResponse;
import kh.edu.acleda.campuslink.modules.profile.entity.SavedItem;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import kh.edu.acleda.campuslink.modules.profile.repository.SavedItemRepository;
import kh.edu.acleda.campuslink.modules.profile.service.SavedItemPreview;
import kh.edu.acleda.campuslink.modules.profile.service.SavedItemPreviewResolver;
import kh.edu.acleda.campuslink.modules.profile.service.SavedItemService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedItemServiceImpl implements SavedItemService {

    private final SavedItemRepository savedItemRepository;
    private final SavedItemPreviewResolver previewResolver;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public SavedItemServiceImpl(SavedItemRepository savedItemRepository,
                                SavedItemPreviewResolver previewResolver,
                                UserRepository userRepository,
                                CurrentUserProvider currentUserProvider) {
        this.savedItemRepository = savedItemRepository;
        this.previewResolver = previewResolver;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedItemResponse> getMine(SavedItemType itemType, Language language) {
        UUID userId = currentUserProvider.getCurrentUserId();
        List<SavedItem> items = itemType == null
            ? savedItemRepository.findByUserIdOrderBySavedAtDesc(userId)
            : savedItemRepository.findByUserIdAndItemTypeOrderBySavedAtDesc(userId, itemType);

        Map<UUID, SavedItemPreview> previews = loadPreviews(items, language);
        return items.stream().map(item -> toResponse(item, previews.get(item.getItemId()))).toList();
    }

    /** Batched per type so the whole list costs one query per distinct type, not one per saved item. */
    private Map<UUID, SavedItemPreview> loadPreviews(List<SavedItem> items, Language language) {
        if (items.isEmpty()) {
            return Map.of();
        }
        Map<SavedItemType, List<UUID>> idsByType = items.stream()
            .collect(Collectors.groupingBy(SavedItem::getItemType,
                Collectors.mapping(SavedItem::getItemId, Collectors.toList())));

        Map<UUID, SavedItemPreview> previews = new HashMap<>();
        idsByType.forEach((type, ids) -> previews.putAll(previewResolver.resolve(type, ids, language)));
        return previews;
    }

    @Override
    @Transactional
    public SavedItemResponse save(SaveItemRequest request, Language language) {
        UUID userId = currentUserProvider.getCurrentUserId();

        SavedItemPreview requestedPreview = null;
        if (request.itemType() == SavedItemType.POST) {
            requestedPreview = previewResolver.resolve(request.itemType(), List.of(request.itemId()), language)
                .get(request.itemId());
            if (requestedPreview == null) {
                throw new NotFoundException("Saved item not found");
            }
        }

        // The unique key is the database backstop.
        User user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));

        // Save is a UI toggle, so tapping it twice must not 500 on the unique
        // (user_id, item_type, item_id) constraint.
        SavedItem item = savedItemRepository
            .findByUserIdAndItemTypeAndItemId(userId, request.itemType(), request.itemId())
            .orElseGet(() -> savedItemRepository.save(SavedItem.builder()
                    .user(user)
                    .itemType(request.itemType())
                    .itemId(request.itemId())
                    .savedAt(LocalDateTime.now())
                    .build()));

        SavedItemPreview preview = requestedPreview != null ? requestedPreview : previewResolver
            .resolve(item.getItemType(), List.of(item.getItemId()), language).get(item.getItemId());
        return toResponse(item, preview);
    }

    @Override
    @Transactional
    public void unsave(SavedItemType itemType, UUID itemId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        SavedItem item = savedItemRepository.findByUserIdAndItemTypeAndItemId(userId, itemType, itemId)
            .orElseThrow(() -> new NotFoundException("Saved item not found"));
        savedItemRepository.delete(item);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSaved(SavedItemType itemType, UUID itemId) {
        return savedItemRepository.existsByUserIdAndItemTypeAndItemId(
            currentUserProvider.getCurrentUserId(), itemType, itemId);
    }

    private SavedItemResponse toResponse(SavedItem item, SavedItemPreview preview) {
        // A missing preview means the target was deleted: the row stays, flagged.
        return new SavedItemResponse(
            item.getId(),
            item.getItemType(),
            item.getItemId(),
            item.getSavedAt(),
            preview != null,
            preview == null ? null : preview.title(),
            preview == null ? null : preview.subtitle(),
            preview == null ? null : preview.imageUrl()
        );
    }
}
