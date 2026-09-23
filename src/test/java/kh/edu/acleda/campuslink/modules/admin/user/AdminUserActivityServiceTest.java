package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.analytics.repository.AdminUserActivityQueryRepository;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserActivityResponse;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminUserActivityServiceTest {

    private AdminUserActivityQueryRepository queryRepository;
    private AdminUserActivityService service;

    @BeforeEach
    void setUp() {
        queryRepository = mock(AdminUserActivityQueryRepository.class);
        service = new AdminUserActivityService(queryRepository);
    }

    @Test
    void mapsAllActivityCountsFromTheReadOnlyProjection() {
        UUID userId = UUID.randomUUID();
        when(queryRepository.findByUserId(userId)).thenReturn(
            new AdminUserActivityQueryRepository.UserActivityCounts(
                true, 3, 5, 2, 4, 1, 6));

        AdminUserActivityResponse response = service.get(userId);

        assertThat(response).isEqualTo(new AdminUserActivityResponse(
            userId, 3, 5, 2, 4, 1, 6));
        verify(queryRepository).findByUserId(userId);
    }

    @Test
    void rejectsUnknownUsersWithoutLeakingAZeroActivityProjection() {
        UUID userId = UUID.randomUUID();
        when(queryRepository.findByUserId(userId)).thenReturn(
            new AdminUserActivityQueryRepository.UserActivityCounts(
                false, 0, 0, 0, 0, 0, 0));

        assertThatThrownBy(() -> service.get(userId))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("User not found");
    }
}
