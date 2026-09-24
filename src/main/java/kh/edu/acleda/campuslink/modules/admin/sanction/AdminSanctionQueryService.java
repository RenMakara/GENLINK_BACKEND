package kh.edu.acleda.campuslink.modules.admin.sanction;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.analytics.AnalyticsTimeZone;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminSanctionListCriteria;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.response.AdminSanctionResponse;
import kh.edu.acleda.campuslink.modules.admin.sanction.entity.UserSanction;
import kh.edu.acleda.campuslink.modules.admin.sanction.repository.UserSanctionRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only query service for the per-user and global enforcement views. */
@Service
public class AdminSanctionQueryService {

    private final UserRepository userRepository;
    private final UserSanctionRepository userSanctionRepository;
    private final Clock clock;

    @Autowired
    public AdminSanctionQueryService(
        UserRepository userRepository,
        UserSanctionRepository userSanctionRepository) {
        this(userRepository, userSanctionRepository, Clock.systemUTC());
    }

    AdminSanctionQueryService(
        UserRepository userRepository,
        UserSanctionRepository userSanctionRepository,
        Clock clock) {
        this.userRepository = userRepository;
        this.userSanctionRepository = userSanctionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminSanctionResponse> listForUser(
        UUID userId,
        AdminSanctionListCriteria criteria) {
        userRepository.findById(userId)
            .orElseThrow(() -> new kh.edu.acleda.campuslink.shared.exception.NotFoundException(
                "User not found with id: " + userId));

        Instant now = clock.instant();
        Specification<UserSanction> specification = specification(userId, criteria, now);
        Page<UserSanction> page = userSanctionRepository.findAll(specification, pageRequest(criteria));
        return PageResponse.from(page, sanction -> toResponse(sanction, now));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminSanctionResponse> listGlobal(AdminSanctionListCriteria criteria) {
        Instant now = clock.instant();
        Page<UserSanction> page = userSanctionRepository.findAll(
            specification(null, criteria, now), pageRequest(criteria));
        return PageResponse.from(page, sanction -> toResponse(sanction, now));
    }

    private static Specification<UserSanction> specification(
        UUID userId,
        AdminSanctionListCriteria criteria,
        Instant now) {
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("deletedAt")));

            if (userId != null) {
                predicates.add(builder.equal(root.get("user").get("id"), userId));
            }
            if (!criteria.types().isEmpty()) {
                predicates.add(root.get("type").in(criteria.types()));
            }
            if (!criteria.severities().isEmpty()) {
                predicates.add(root.get("severity").in(criteria.severities()));
            }
            if (criteria.adminUserId() != null) {
                predicates.add(builder.equal(root.get("adminUser").get("id"), criteria.adminUserId()));
            }
            if (criteria.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                    root.<Instant>get("startsAt"), AnalyticsTimeZone.startOfDay(criteria.from())));
            }
            if (criteria.to() != null) {
                predicates.add(builder.lessThan(
                    root.<Instant>get("startsAt"), AnalyticsTimeZone.endOfDayExclusive(criteria.to())));
            }
            if (criteria.activeOnly()) {
                predicates.add(builder.isNull(root.get("liftedAt")));
                predicates.add(builder.or(
                    builder.isNull(root.get("expiresAt")),
                    builder.greaterThan(root.<Instant>get("expiresAt"), now)));
            }

            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static Pageable pageRequest(AdminSanctionListCriteria criteria) {
        return PageRequest.of(
            criteria.page(),
            criteria.size(),
            Sort.by(Sort.Order.desc("startsAt"), Sort.Order.desc("id")));
    }

    private static AdminSanctionResponse toResponse(UserSanction sanction, Instant now) {
        User user = sanction.getUser();
        AdminUser issuedBy = sanction.getAdminUser();
        AdminUser liftedBy = sanction.getLiftedBy();
        return new AdminSanctionResponse(
            sanction.getId(),
            user == null ? null : user.getId(),
            user == null ? null : user.getFullName(),
            sanction.getType(),
            sanction.getSeverity(),
            sanction.getReason(),
            sanction.getMessageEn(),
            sanction.getMessageKm(),
            sanction.getPreviousStatus(),
            sanction.getStartsAt(),
            sanction.getExpiresAt(),
            sanction.getAcknowledgedAt(),
            sanction.getLiftedAt(),
            sanction.getLiftReason(),
            issuedBy == null ? null : issuedBy.getId(),
            adminName(issuedBy),
            liftedBy == null ? null : liftedBy.getId(),
            adminName(liftedBy),
            sanction.getDeletedAt() == null && sanction.isInForce(now));
    }

    private static String adminName(AdminUser admin) {
        return admin == null || admin.getUser() == null ? null : admin.getUser().getFullName();
    }
}
