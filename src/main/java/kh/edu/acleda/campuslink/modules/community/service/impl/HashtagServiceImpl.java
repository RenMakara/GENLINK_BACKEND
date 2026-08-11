package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kh.edu.acleda.campuslink.modules.community.entity.Hashtag;
import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.entity.PostHashtag;
import kh.edu.acleda.campuslink.modules.community.repository.HashtagRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostHashtagRepository;
import kh.edu.acleda.campuslink.modules.community.service.HashtagService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HashtagServiceImpl implements HashtagService {

    /**
     * Must start with a letter, so "#1" and "#123" are not tags.
     *
     * <p>\p{M} is required, not decorative: Khmer writes a syllable as a consonant plus
     * vowel signs and coeng, which Unicode classifies as marks rather than letters.
     * Without it "#បច្ចេកវិទ្យា" is silently truncated at the first vowel sign, and \w
     * would drop Khmer entirely.
     */
    private static final Pattern HASHTAG = Pattern.compile("#([\\p{L}][\\p{L}\\p{M}\\p{N}_]{0,49})");
    private static final int MAX_TAGS_PER_POST = 10;

    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;

    public HashtagServiceImpl(HashtagRepository hashtagRepository,
                              PostHashtagRepository postHashtagRepository) {
        this.hashtagRepository = hashtagRepository;
        this.postHashtagRepository = postHashtagRepository;
    }

    @Override
    @Transactional
    public void syncForPost(Post post, String contentText) {
        Set<String> tags = extract(contentText);

        // Editing a post rebuilds its links, so drop the old ones (decrementing their usage
        // counts) first. Without this, re-syncing an edited post re-incremented all its current
        // tags on top of the old count, inflating trending on every edit. Net effect for an
        // unchanged tag is -1 then +1 = 0.
        clearForPost(post);
        if (tags.isEmpty()) {
            return;
        }

        Map<String, Hashtag> existing = new HashMap<>();
        hashtagRepository.findByTagIn(tags).forEach(h -> existing.put(h.getTag(), h));

        List<PostHashtag> links = new ArrayList<>();
        for (String tag : tags) {
            Hashtag hashtag = existing.get(tag);
            if (hashtag == null) {
                hashtag = hashtagRepository.save(Hashtag.builder().tag(tag).usageCount(0).build());
            }
            hashtag.setUsageCount(hashtag.getUsageCount() == null ? 1 : hashtag.getUsageCount() + 1);
            hashtagRepository.save(hashtag);
            links.add(PostHashtag.builder().post(post).hashtag(hashtag).build());
        }
        postHashtagRepository.saveAll(links);
    }

    @Override
    @Transactional
    public void clearForPost(Post post) {
        // Decrement the usage count of every tag this post was linked to, then remove the links.
        // Without this a deleted post left its tags inflating trending indefinitely.
        for (PostHashtag oldLink : postHashtagRepository.findByPostId(post.getId())) {
            Hashtag hashtag = oldLink.getHashtag();
            int current = hashtag.getUsageCount() == null ? 0 : hashtag.getUsageCount();
            hashtag.setUsageCount(Math.max(0, current - 1));
            hashtagRepository.save(hashtag);
        }
        postHashtagRepository.deleteByPostId(post.getId());
        postHashtagRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<String>> tagsForPosts(Set<UUID> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<String>> byPost = new HashMap<>();
        for (PostHashtag link : postHashtagRepository.findByPostIdIn(postIds)) {
            byPost.computeIfAbsent(link.getPost().getId(), key -> new ArrayList<>())
                .add(link.getHashtag().getTag());
        }
        return byPost;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> trending(int limit) {
        return hashtagRepository.findByOrderByUsageCountDesc(PageRequest.of(0, limit))
            .stream()
            .map(Hashtag::getTag)
            .toList();
    }

    /**
     * Lower-cased and de-duplicated: #Flutter and #flutter are one tag, and repeating a
     * tag in one post must not count twice or breach the unique (post, hashtag) index.
     */
    private Set<String> extract(String contentText) {
        if (contentText == null || contentText.isBlank()) {
            return Set.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        Matcher matcher = HASHTAG.matcher(contentText);
        while (matcher.find() && tags.size() < MAX_TAGS_PER_POST) {
            tags.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return tags;
    }
}
