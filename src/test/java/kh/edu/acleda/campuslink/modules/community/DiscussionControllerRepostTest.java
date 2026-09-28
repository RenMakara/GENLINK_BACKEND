package kh.edu.acleda.campuslink.modules.community;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.controller.DiscussionController;
import kh.edu.acleda.campuslink.modules.community.service.DiscussionService;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DiscussionControllerRepostTest {

    @Test
    void discussionRepostEndpointsRejectWithoutCreatingOrRemovingReposts() {
        DiscussionService discussionService = mock(DiscussionService.class);
        DiscussionController controller = new DiscussionController(discussionService);
        UUID discussionId = UUID.randomUUID();

        assertThatThrownBy(() -> controller.repost(discussionId))
            .isInstanceOf(ApiException.class)
            .hasMessage("Discussions cannot be reposted");
        assertThatThrownBy(() -> controller.unrepost(discussionId))
            .isInstanceOf(ApiException.class)
            .hasMessage("Discussions cannot be reposted");

        verifyNoInteractions(discussionService);
    }
}
