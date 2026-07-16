package kh.edu.acleda.campuslink.modules.profile.service;

import java.time.Instant;
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
import kh.edu.acleda.campuslink.modules.entertainment.repository.ShortContentRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.FinancialLessonRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.MotivationContentRepository;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.localization.LocalizedTextSelector;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns saved_items pointers into row previews.
 *
 * <p>saved_items is polymorphic, so the title of a saved thing lives in a different
 * table per type. This is the one place in the profile module that reads other
 * domains, and it is read-only and preview-shaped: no domain entity escapes.
 *
 * <p>Lookups are batched per type — one query per distinct type in the list, not one
 * per saved item — so a 50-item saved list costs at most 7 queries.
 */
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

    public SavedItemPreviewResolver(PostRepository postRepository,
                                    ForumDiscussionRepository forumDiscussionRepository,
                                    EventRepository eventRepository,
                                    OpportunityRepository opportunityRepository,
                                    MotivationContentRepository motivationContentRepository,
                                    FinancialLessonRepository financialLessonRepository,
                                    ShortContentRepository shortContentRepository) {
        this.postRepository = postRepository;
        this.forumDiscussionRepository = forumDiscussionRepository;
        this.eventRepository = eventRepository;
        this.opportunityRepository = opportunityRepository;
        this.motivationContentRepository = motivationContentRepository;
        this.financialLessonRepository = financialLessonRepository;
        this.shortContentRepository = shortContentRepository;
    }

    /**
     * @return previews keyed by item id. An id missing from the map means the target
     *         was deleted; the caller reports that item as unavailable.
     */
    @Transactional(readOnly = true)
    public Map<UUID, SavedItemPreview> resolve(SavedItemType type, Collection<UUID> ids, Language language) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return switch (type) {
            case POST -> index(postRepository.findAllById(ids), post -> post.getDeletedAt() != null ? null
                : new SavedItemPreview(truncate(post.getContentText()), null, post.getImageUrl()));
            case DISCUSSION -> index(forumDiscussionRepository.findAllById(ids), d -> d.getDeletedAt() != null ? null
                : new SavedItemPreview(d.getTitle(), d.getCategory(), null));
            case EVENT -> index(eventRepository.findAllById(ids), e -> e.getDeletedAt() != null ? null
                : new SavedItemPreview(e.getTitle(), e.getLocationName(), e.getCoverImageUrl()));
            case OPPORTUNITY -> index(opportunityRepository.findAllById(ids), o -> o.getDeletedAt() != null ? null
                : new SavedItemPreview(o.getTitle(), o.getOrganization(), null));
            case MOTIVATION -> index(motivationContentRepository.findAllById(ids), m -> new SavedItemPreview(
                LocalizedTextSelector.select(m.getTitleEn(), m.getTitleKm(), language), null, null));
            case LESSON -> index(financialLessonRepository.findAllById(ids), l -> new SavedItemPreview(
                LocalizedTextSelector.select(l.getTitleEn(), l.getTitleKm(), language), null, null));
            case SHORT_CONTENT -> index(shortContentRepository.findAllById(ids), s -> new SavedItemPreview(
                LocalizedTextSelector.select(s.getTitleEn(), s.getTitleKm(), language), null, s.getThumbnailUrl()));
        };
    }

    private <T extends BaseEntity> Map<UUID, SavedItemPreview> index(List<T> entities,
                                                                     Function<T, SavedItemPreview> toPreview) {
        Map<UUID, SavedItemPreview> previews = new HashMap<>();
        for (T entity : entities) {
            SavedItemPreview preview = toPreview.apply(entity);
            // null means soft-deleted: leave it out so it reads as unavailable.
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
