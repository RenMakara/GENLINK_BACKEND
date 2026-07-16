package kh.edu.acleda.campuslink.modules.profile.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.dto.request.SaveItemRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.SavedItemResponse;
import kh.edu.acleda.campuslink.modules.profile.entity.SavedItem;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import kh.edu.acleda.campuslink.modules.profile.repository.SavedItemRepository;
import kh.edu.acleda.campuslink.modules.profile.service.SavedItemService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedItemServiceImpl implements SavedItemService {

    private final SavedItemRepository savedItemRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public SavedItemServiceImpl(SavedItemRepository savedItemRepository,
                                UserRepository userRepository,
                                CurrentUserProvider currentUserProvider) {
        this.savedItemRepository = savedItemRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedItemResponse> getMine(SavedItemType itemType) {
        UUID userId = currentUserProvider.getCurrentUserId();
        List<SavedItem> items = itemType == null
            ? savedItemRepository.findByUserIdOrderBySavedAtDesc(userId)
            : savedItemRepository.findByUserIdAndItemTypeOrderBySavedAtDesc(userId, itemType);
        return items.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public SavedItemResponse save(SaveItemRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();

        // Save is a UI toggle, so tapping it twice must not 500 on the unique
        // (user_id, item_type, item_id) constraint.
        return savedItemRepository.findByUserIdAndItemTypeAndItemId(userId, request.itemType(), request.itemId())
            .map(this::toResponse)
            .orElseGet(() -> {
                User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found"));
                SavedItem item = SavedItem.builder()
                    .user(user)
                    .itemType(request.itemType())
                    .itemId(request.itemId())
                    .savedAt(LocalDateTime.now())
                    .build();
                return toResponse(savedItemRepository.save(item));
            });
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

    private SavedItemResponse toResponse(SavedItem item) {
        return new SavedItemResponse(item.getId(), item.getItemType(), item.getItemId(), item.getSavedAt());
    }
}
