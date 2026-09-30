package kh.edu.acleda.campuslink.modules.profile.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import kh.edu.acleda.campuslink.modules.community.repository.EventRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumDiscussionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.OpportunityRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostRepository;
import kh.edu.acleda.campuslink.modules.community.service.EventVisibility;
import kh.edu.acleda.campuslink.modules.community.service.PostVisibilityService;
import kh.edu.acleda.campuslink.modules.media.MediaService;
import kh.edu.acleda.campuslink.modules.media.entity.MediaAttachmentRole;
import kh.edu.acleda.campuslink.modules.media.entity.MediaResourceType;
import kh.edu.acleda.campuslink.modules.entertainment.repository.ShortContentRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.FinancialLessonRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.MotivationContentRepository;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.localization.LocalizedTextSelector;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Turns saved_items pointers into row previews. */
@Component
public class SavedItemPreviewResolver {

    private static final int POST_TITLE_MAX = 80;

    private final PostRepository postRepository;
    private final ForumDiscussionRepository forumDiscussionRepository;
    private final EventRepository eventRepository;
    private final OpportunityRepository opportunityRepository;
    private final MotivationContentRepository motivationContentRepository;
    private final FinancialLessonRepository financialLessonRepository;
    private final ShortContentRepository shortContentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PostVisibilityService postVisibilityService;
    private final MediaService mediaService;

    public SavedItemPreviewResolver(PostRepository postRepository,
                                    ForumDiscussionRepository forumDiscussionRepository,
                                    EventRepository eventRepository,
                                    OpportunityRepository opportunityRepository,
                                    MotivationContentRepository motivationContentRepository,
                                    FinancialLessonRepository financialLessonRepository,
                                    ShortContentRepository shortContentRepository,
                                    CurrentUserProvider currentUserProvider,
                                    PostVisibilityService postVisibilityService,
                                    MediaService mediaService) {
        this.postRepository = postRepository;
        this.forumDiscussionRepository = forumDiscussionRepository;
        this.eventRepository = eventRepository;
        this.opportunityRepository = opportunityRepository;
        this.motivationContentRepository = motivationContentRepository;
        this.financialLessonRepository = financialLessonRepository;
        this.shortContentRepository = shortContentRepository;
        this.currentUserProvider = currentUserProvider;
        this.postVisibilityService = postVisibilityService;
        this.mediaService = mediaService;
    }

    /** Compatibility constructor retained for direct tests of non-post previews. */
    @Autowired
    public SavedItemPreviewResolver(PostRepository postRepository,
                                    ForumDiscussionRepository forumDiscussionRepository,
                                    EventRepository eventRepository,
                                    OpportunityRepository opportunityRepository,
                                    MotivationContentRepository motivationContentRepository,
                                    FinancialLessonRepository financialLessonRepository,
                                    ShortContentRepository shortContentRepository,
                                    CurrentUserProvider currentUserProvider) {
        this(postRepository, forumDiscussionRepository, eventRepository, opportunityRepository,
            motivationContentRepository, financialLessonRepository, shortContentRepository,
            currentUserProvider, null, null);
    }

    /** @return previews keyed by item id. An id missing from the map means the target */
    @Transactional(readOnly = true)
    public Map<UUID, SavedItemPreview> resolve(SavedItemType type, Collection<UUID> ids, Language language) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return switch (type) {
            case POST -> index(postRepository.findAllById(ids), post -> {
                UUID viewerId = currentUserProvider.getCurrentUserId();
                if (post.getDeletedAt() != null || postVisibilityService == null
                    || !postVisibilityService.canView(post, viewerId)) {
                    return null;
                }
                List<MediaService.AttachedMedia> attached = mediaService == null ? List.of()
                    : mediaService.resolveAttachedMedia(MediaResourceType.POST, post.getId());
                String imageUrl = attached.stream()
                    .filter(media -> media.role() == MediaAttachmentRole.IMAGE)
                    .map(MediaService.AttachedMedia::url)
                    .findFirst()
                    .orElse(post.getVisibility() == kh.edu.acleda.campuslink.modules.community.enums.Visibility.PUBLIC
                        ? post.getImageUrl() : null);
                return new SavedItemPreview(truncate(post.getContentText()), null, imageUrl);
            });
            case DISCUSSION -> index(forumDiscussionRepository.findAllById(ids), d -> d.getDeletedAt() != null ? null
                : new SavedItemPreview(d.getTitle(), d.getCategory(), null));
            case EVENT -> eventPreviews(ids);
            case OPPORTUNITY -> index(opportunityRepository.findAllById(ids), o -> o.getDeletedAt() != null ? null
                : new SavedItemPreview(o.getTitle(), o.getOrganization(), null));
            case MOTIVATION -> index(motivationContentRepository.findAllById(ids), m -> new SavedItemPreview(
                LocalizedTextSelector.select(m.getTitleEn(), m.getTitleKm(), language), null, null));
            case LESSON -> index(financialLessonRepository.findAllById(ids), l -> new SavedItemPreview(
                LocalizedTextSelector.select(l.getTitleEn(), l.getTitleKm(), language), null, null));
            case SHORT_CONTENT -> index(shortContentRepository.findAllById(ids), s -> s.getDeletedAt() != null ? null
                : new SavedItemPreview(
                    LocalizedTextSelector.select(s.getTitleEn(), s.getTitleKm(), language),
                    null, s.getThumbnailUrl()));
        };
    }

    private Map<UUID, SavedItemPreview> eventPreviews(Collection<UUID> ids) {
        UUID viewerId = currentUserProvider.getCurrentUserId();
        return index(eventRepository.findAllById(ids), event -> EventVisibility.canView(event, viewerId)
            ? new SavedItemPreview(event.getTitle(), event.getLocationName(), event.getCoverImageUrl())
            : null);
    }

    private <T extends BaseEntity> Map<UUID, SavedItemPreview> index(List<T> entities,
                                                                     Function<T, SavedItemPreview> toPreview) {
        Map<UUID, SavedItemPreview> previews = new HashMap<>();
        for (T entity : entities) {
            SavedItemPreview preview = toPreview.apply(entity);
            // null means unavailable: leave it out so the caller can flag the row.
            if (preview != null) {
                previews.put(entity.getId(), preview);
            }
        }
        return previews;
    }

    /** Posts have no title column, so the body stands in for one. */
    private String truncate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.strip();
        return trimmed.length() <= POST_TITLE_MAX ? trimmed : trimmed.substring(0, POST_TITLE_MAX) + "...";
    }
}
