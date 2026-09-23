package kh.edu.acleda.campuslink.modules.admin.user;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.analytics.repository.AdminUserActivityQueryRepository;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserActivityResponse;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only activity projection service for the admin user detail page. */
@Service
public class AdminUserActivityService {

    private final AdminUserActivityQueryRepository queryRepository;

    public AdminUserActivityService(AdminUserActivityQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public AdminUserActivityResponse get(UUID userId) {
        AdminUserActivityQueryRepository.UserActivityCounts counts =
            queryRepository.findByUserId(userId);
        if (!counts.userExists()) {
            throw new NotFoundException("User not found with id: " + userId);
        }
        return new AdminUserActivityResponse(
            userId,
            counts.posts(),
            counts.comments(),
            counts.communitiesJoined(),
            counts.eventsRsvpd(),
            counts.reportsFiled(),
            counts.reportsReceived()
        );
    }
}
