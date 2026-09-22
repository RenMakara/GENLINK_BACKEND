package kh.edu.acleda.campuslink.modules.profile;

import kh.edu.acleda.campuslink.modules.profile.dto.request.PortfolioItemCreateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.request.PortfolioItemUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.PortfolioItemResponse;
import kh.edu.acleda.campuslink.modules.profile.entity.PortfolioItem;
import kh.edu.acleda.campuslink.modules.profile.repository.PortfolioItemRepository;
import kh.edu.acleda.campuslink.modules.profile.service.impl.PortfolioServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceImplTest {

    @Mock
    private PortfolioItemRepository portfolioItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private PortfolioServiceImpl newService() {
        return new PortfolioServiceImpl(portfolioItemRepository, userRepository, currentUserProvider);
    }

    @Test
    void createPortfolioItemSuccessfully() {
        PortfolioServiceImpl service = newService();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setEmail("student@example.com");

        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(portfolioItemRepository.save(any(PortfolioItem.class))).thenAnswer(invocation -> {
            PortfolioItem item = invocation.getArgument(0);
            return item;
        });

        PortfolioItemCreateRequest request = new PortfolioItemCreateRequest(
            "Portfolio Project",
            "Project Description",
            "https://example.com/project",
            "https://example.com/cover.png"
        );

        PortfolioItemResponse response = service.create(request);

        assertThat(response.title()).isEqualTo("Portfolio Project");
        assertThat(response.description()).isEqualTo("Project Description");
        assertThat(response.link()).isEqualTo("https://example.com/project");
        assertThat(response.imageUrl()).isEqualTo("https://example.com/cover.png");

        ArgumentCaptor<PortfolioItem> captor = ArgumentCaptor.forClass(PortfolioItem.class);
        verify(portfolioItemRepository).save(captor.capture());
        PortfolioItem saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("Portfolio Project");
        assertThat(saved.getUser()).isEqualTo(user);
    }

    @Test
    void getMineReturnsOnlyOwnedActiveItems() {
        PortfolioServiceImpl service = newService();
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);

        PortfolioItem item = PortfolioItem.builder()
            .title("Project 1")
            .build();
        when(portfolioItemRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId))
            .thenReturn(List.of(item));

        List<PortfolioItemResponse> mine = service.getMine();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).title()).isEqualTo("Project 1");
    }
}
