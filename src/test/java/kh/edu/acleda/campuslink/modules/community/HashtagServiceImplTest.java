package kh.edu.acleda.campuslink.modules.community;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.Hashtag;
import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.entity.PostHashtag;
import kh.edu.acleda.campuslink.modules.community.repository.HashtagRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostHashtagRepository;
import kh.edu.acleda.campuslink.modules.community.service.impl.HashtagServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HashtagServiceImplTest {

    @Mock private HashtagRepository hashtagRepository;
    @Mock private PostHashtagRepository postHashtagRepository;

    private HashtagServiceImpl service;
    private Post post;

    @BeforeEach
    void setUp() {
        service = new HashtagServiceImpl(hashtagRepository, postHashtagRepository);
        post = Post.builder().build();
        ReflectionTestUtils.setField(post, "id", UUID.randomUUID());
        when(hashtagRepository.findByTagIn(anyCollection())).thenReturn(List.of());
        when(hashtagRepository.save(any(Hashtag.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void extractsTagsFromContent() {
        service.syncForPost(post, "Learning #Flutter and #dart today");

        assertThat(savedTags()).containsExactlyInAnyOrder("flutter", "dart");
    }

    /** #Flutter and #flutter are the same tag, so trending does not split its count. */
    @Test
    void tagsAreLowercased() {
        service.syncForPost(post, "#FLUTTER #Flutter #flutter");

        assertThat(savedTags()).containsExactly("flutter");
    }

    /** post_hashtags has UNIQUE(post_id, hashtag_id), so a repeat must not double-link. */
    @Test
    void repeatedTagInOnePostIsLinkedOnce() {
        service.syncForPost(post, "#khmer first, #khmer always");

        assertThat(savedLinks()).hasSize(1);
    }

    /** \w would miss Khmer script; the app is Khmer-first, so tags must survive it. */
    @Test
    void khmerTagsAreExtracted() {
        service.syncForPost(post, "#បច្ចេកវិទ្យា is technology");

        assertThat(savedTags()).containsExactly("បច្ចេកវិទ្យា");
    }

    @Test
    void numericOnlyTagsAreIgnored() {
        service.syncForPost(post, "#123 #1st");

        assertThat(savedTags()).isEmpty();
    }

    @Test
    void contentWithNoTagsLinksNothing() {
        service.syncForPost(post, "just a normal post");

        assertThat(savedLinks()).isEmpty();
    }

    @Test
    void nullContentIsSafe() {
        service.syncForPost(post, null);

        assertThat(savedLinks()).isEmpty();
    }

    /** Editing a post can remove a tag, so links are rebuilt rather than appended. */
    @Test
    void existingLinksAreClearedBeforeRelinking() {
        service.syncForPost(post, "#flutter");

        verify(postHashtagRepository).deleteByPostId(post.getId());
    }

    @Test
    void usageCountIsIncrementedForAnExistingTag() {
        Hashtag existing = Hashtag.builder().tag("flutter").usageCount(4).build();
        when(hashtagRepository.findByTagIn(anyCollection())).thenReturn(List.of(existing));

        service.syncForPost(post, "#flutter");

        assertThat(existing.getUsageCount()).isEqualTo(5);
    }

    /** Deleting a post decrements its tags and removes the links, so trending stops counting them. */
    @Test
    void clearForPostDecrementsUsageAndRemovesLinks() {
        Hashtag tag = Hashtag.builder().tag("flutter").usageCount(3).build();
        PostHashtag link = PostHashtag.builder().post(post).hashtag(tag).build();
        when(postHashtagRepository.findByPostId(post.getId())).thenReturn(List.of(link));

        service.clearForPost(post);

        assertThat(tag.getUsageCount()).isEqualTo(2);
        verify(postHashtagRepository).deleteByPostId(post.getId());
    }

    /** -1 for the old link then +1 for the new one nets to zero: no edit inflation. */
    @Test
    void reSyncingAnUnchangedTagDoesNotInflateItsCount() {
        Hashtag flutter = Hashtag.builder().tag("flutter").usageCount(5).build();
        when(postHashtagRepository.findByPostId(post.getId()))
            .thenReturn(List.of(PostHashtag.builder().post(post).hashtag(flutter).build()));
        when(hashtagRepository.findByTagIn(anyCollection())).thenReturn(List.of(flutter));

        service.syncForPost(post, "still about #flutter");

        assertThat(flutter.getUsageCount()).isEqualTo(5);
    }

    /** A tag removed on edit loses its count and is not re-added. */
    @Test
    void removingATagOnEditDecrementsItsCount() {
        Hashtag dart = Hashtag.builder().tag("dart").usageCount(3).build();
        when(postHashtagRepository.findByPostId(post.getId()))
            .thenReturn(List.of(PostHashtag.builder().post(post).hashtag(dart).build()));

        service.syncForPost(post, "no tags anymore");

        assertThat(dart.getUsageCount()).isEqualTo(2);
    }

    @Test
    void trendingUsesOnlyTagsFromVisiblePublicPosts() {
        when(hashtagRepository.findTrendingPublicTags(any())).thenReturn(List.of("public-tag"));

        assertThat(service.trending(5)).containsExactly("public-tag");

        verify(hashtagRepository).findTrendingPublicTags(any());
        verify(hashtagRepository, never()).findByOrderByUsageCountDesc(any());
    }

    @SuppressWarnings("unchecked")
    private List<String> savedTags() {
        ArgumentCaptor<Hashtag> captor = ArgumentCaptor.forClass(Hashtag.class);
        verify(hashtagRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        List<String> tags = new ArrayList<>();
        captor.getAllValues().forEach(h -> {
            if (!tags.contains(h.getTag())) {
                tags.add(h.getTag());
            }
        });
        return tags;
    }

    @SuppressWarnings("unchecked")
    private List<PostHashtag> savedLinks() {
        ArgumentCaptor<List<PostHashtag>> captor = ArgumentCaptor.forClass(List.class);
        verify(postHashtagRepository, org.mockito.Mockito.atLeast(0)).saveAll(captor.capture());
        return captor.getAllValues().isEmpty() ? List.of() : captor.getValue();
    }
}
