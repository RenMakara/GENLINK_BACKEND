package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.request.ShortContentCreateRequest;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.ShortContentResponse;
import kh.edu.acleda.campuslink.modules.entertainment.entity.ShortContent;
import kh.edu.acleda.campuslink.modules.entertainment.entity.ShortContentLike;
import kh.edu.acleda.campuslink.modules.entertainment.entity.ShortContentSave;
import kh.edu.acleda.campuslink.modules.entertainment.repository.ShortContentLikeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.ShortContentRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.ShortContentSaveRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.ShortContentService;
import kh.edu.acleda.campuslink.modules.media.MediaService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.localization.LocalizedTextSelector;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortContentServiceImpl implements ShortContentService {

    private final ShortContentRepository shortContentRepository;
    private final ShortContentLikeRepository likeRepository;
    private final ShortContentSaveRepository saveRepository;
    private final MediaService mediaService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public ShortContentServiceImpl(ShortContentRepository shortContentRepository,
                                   ShortContentLikeRepository likeRepository,
                                   ShortContentSaveRepository saveRepository,
                                   MediaService mediaService,
                                   UserRepository userRepository,
                                   CurrentUserProvider currentUserProvider) {
        this.shortContentRepository = shortContentRepository;
        this.likeRepository = likeRepository;
        this.saveRepository = saveRepository;
        this.mediaService = mediaService;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public ShortContentResponse create(ShortContentCreateRequest request, Language language) {
        String videoUrl = request.videoMediaId() == null
            ? request.videoUrl()
            : mediaService.resolveConfirmedUrl(request.videoMediaId());
        String thumbnailUrl = request.thumbnailMediaId() == null
            ? request.thumbnailUrl()
            : mediaService.resolveConfirmedUrl(request.thumbnailMediaId());
        ShortContent content = shortContentRepository.save(ShortContent.builder()
            .titleEn(request.title())
            .descriptionEn(request.description())
            .category(request.category())
            .videoUrl(videoUrl)
            .thumbnailUrl(thumbnailUrl)
            .creator(loadCurrentUser())
            .likeCount(0).saveCount(0).shareCount(0)
            .build());
        return toResponse(content, false, false, language);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        ShortContent content = loadContent(id);
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        if (content.getCreator() == null || !currentUserId.equals(content.getCreator().getId())) {
            // Do not reveal whether another user's content exists.
            throw new NotFoundException("Short content not found");
        }
        content.setDeletedAt(Instant.now());
        shortContentRepository.save(content);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShortContentResponse> getFeed(String category, Pageable pageable, Language language) {
        Page<ShortContent> page = category == null
            ? shortContentRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(pageable)
            : shortContentRepository.findByCategoryAndDeletedAtIsNullOrderByCreatedAtDesc(category, pageable);
        List<ShortContentResponse> content = hydrate(page.getContent(), language);
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    @Override
    @Transactional(readOnly = true)
    public ShortContentResponse getById(UUID id, Language language) {
        ShortContent content = loadContent(id);
        UUID userId = currentUserProvider.getCurrentUserId();
        return toResponse(content,
            likeRepository.findByUserIdAndShortContentId(userId, id).isPresent(),
            saveRepository.findByUserIdAndShortContentId(userId, id).isPresent(), language);
    }

    @Override
    @Transactional
    public ShortContentResponse like(UUID id, Language language) {
        ShortContent content = loadContent(id);
        UUID userId = currentUserProvider.getCurrentUserId();
        if (likeRepository.findByUserIdAndShortContentId(userId, id).isEmpty()) {
            try {
                likeRepository.saveAndFlush(ShortContentLike.builder()
                    .user(loadCurrentUser()).shortContent(content).build());
                shortContentRepository.incrementLike(id);
            } catch (DataIntegrityViolationException alreadyLiked) {
                // Concurrent like won the UNIQUE race; it did the count.
            }
        }
        return toResponse(loadContent(id), true, isSaved(userId, id), language);
    }

    @Override
    @Transactional
    public ShortContentResponse unlike(UUID id, Language language) {
        loadContent(id);
        UUID userId = currentUserProvider.getCurrentUserId();
        likeRepository.findByUserIdAndShortContentId(userId, id).ifPresent(like -> {
            likeRepository.delete(like);
            shortContentRepository.decrementLike(id);
        });
        return toResponse(loadContent(id), false, isSaved(userId, id), language);
    }

    @Override
    @Transactional
    public ShortContentResponse save(UUID id, Language language) {
        ShortContent content = loadContent(id);
        UUID userId = currentUserProvider.getCurrentUserId();
        if (saveRepository.findByUserIdAndShortContentId(userId, id).isEmpty()) {
            try {
                saveRepository.saveAndFlush(ShortContentSave.builder()
                    .user(loadCurrentUser()).shortContent(content).build());
                shortContentRepository.incrementSave(id);
            } catch (DataIntegrityViolationException alreadySaved) {
                // Concurrent save won the UNIQUE race.
            }
        }
        return toResponse(loadContent(id), isLiked(userId, id), true, language);
    }

    @Override
    @Transactional
    public ShortContentResponse unsave(UUID id, Language language) {
        loadContent(id);
        UUID userId = currentUserProvider.getCurrentUserId();
        saveRepository.findByUserIdAndShortContentId(userId, id).ifPresent(save -> {
            saveRepository.delete(save);
            shortContentRepository.decrementSave(id);
        });
        return toResponse(loadContent(id), isLiked(userId, id), false, language);
    }

    @Override
    @Transactional
    public ShortContentResponse share(UUID id, Language language) {
        loadContent(id);
        shortContentRepository.incrementShare(id);
        UUID userId = currentUserProvider.getCurrentUserId();
        return toResponse(loadContent(id), isLiked(userId, id), isSaved(userId, id), language);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShortContentResponse> getSaved(Language language) {
        UUID userId = currentUserProvider.getCurrentUserId();
        List<ShortContent> saved = saveRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(ShortContentSave::getShortContent)
            .filter(c -> c != null && c.getDeletedAt() == null)
            .toList();
        return hydrate(saved, language);
    }

    /** Batches the liked/saved lookups for a list, avoiding a query per item. */
    private List<ShortContentResponse> hydrate(List<ShortContent> items, Language language) {
        if (items.isEmpty()) {
            return List.of();
        }
        UUID userId = currentUserProvider.getCurrentUserId();
        List<UUID> ids = items.stream().map(ShortContent::getId).toList();
        Set<UUID> liked = new HashSet<>();
        for (ShortContentLike l : likeRepository.findByUserIdAndShortContentIdIn(userId, ids)) {
            if (l.getShortContent() != null) {
                liked.add(l.getShortContent().getId());
            }
        }
        Set<UUID> saved = new HashSet<>();
        for (ShortContentSave s : saveRepository.findByUserIdAndShortContentIdIn(userId, ids)) {
            if (s.getShortContent() != null) {
                saved.add(s.getShortContent().getId());
            }
        }
        return items.stream()
            .map(c -> toResponse(c, liked.contains(c.getId()), saved.contains(c.getId()), language))
            .toList();
    }

    private boolean isLiked(UUID userId, UUID contentId) {
        return likeRepository.findByUserIdAndShortContentId(userId, contentId).isPresent();
    }

    private boolean isSaved(UUID userId, UUID contentId) {
        return saveRepository.findByUserIdAndShortContentId(userId, contentId).isPresent();
    }

    private ShortContentResponse toResponse(ShortContent c, boolean liked, boolean saved, Language language) {
        return new ShortContentResponse(c.getId(),
            LocalizedTextSelector.select(c.getTitleEn(), c.getTitleKm(), language),
            LocalizedTextSelector.select(c.getDescriptionEn(), c.getDescriptionKm(), language),
            c.getCategory(), c.getVideoUrl(), c.getThumbnailUrl(),
            c.getCreator() == null ? null : c.getCreator().getId(),
            c.getCreator() == null ? null : c.getCreator().getFullName(),
            c.getCreator() == null ? null : c.getCreator().getUsername(),
            c.getCreator() == null ? null : c.getCreator().getAvatarUrl(),
            orZero(c.getLikeCount()), orZero(c.getSaveCount()), orZero(c.getShareCount()), liked, saved);
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private ShortContent loadContent(UUID id) {
        return shortContentRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("Short content not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
