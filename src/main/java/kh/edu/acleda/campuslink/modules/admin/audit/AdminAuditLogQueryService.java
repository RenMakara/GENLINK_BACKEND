package kh.edu.acleda.campuslink.modules.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.analytics.AnalyticsTimeZone;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.request.AdminAuditLogCriteria;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.response.AdminAuditLogDetailResponse;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.response.AdminAuditLogSummaryResponse;
import kh.edu.acleda.campuslink.modules.admin.audit.entity.AdminAuditLog;
import kh.edu.acleda.campuslink.modules.admin.audit.repository.AdminAuditLogRepository;
import kh.edu.acleda.campuslink.modules.admin.rbac.PermissionScopeResolver;
import kh.edu.acleda.campuslink.modules.admin.rbac.PermissionScopeResolver.AuditLogScope;
import kh.edu.acleda.campuslink.modules.admin.rbac.PermissionScopeResolver.Visibility;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, scope-aware access to the append-only administrator audit trail. */
@Service
public class AdminAuditLogQueryService {

    private static final String NOT_FOUND = "Audit log not found";

    private final AdminAuditLogRepository repository;
    private final PermissionScopeResolver scopeResolver;
    private final ObjectMapper objectMapper;

    public AdminAuditLogQueryService(
        AdminAuditLogRepository repository,
        PermissionScopeResolver scopeResolver,
        ObjectMapper objectMapper) {
        this.repository = repository;
        this.scopeResolver = scopeResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAuditLogSummaryResponse> list(AdminAuditLogCriteria criteria) {
        AuditLogScope scope = scopeResolver.resolveAuditLogScope();
        Page<AdminAuditLog> page = findPage(criteria, scope, criteria.page(), criteria.size());
        return PageResponse.from(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public AdminAuditLogDetailResponse detail(UUID auditLogId) {
        AuditLogScope scope = scopeResolver.resolveAuditLogScope();
        AdminAuditLog log = repository.findById(auditLogId)
            .filter(candidate -> canSee(candidate, scope))
            .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        return toDetail(log);
    }

    /** Used by the bounded export writer after it captures one immutable scope decision. */
    @Transactional(readOnly = true)
    Page<AdminAuditLog> findPage(
        AdminAuditLogCriteria criteria,
        AuditLogScope scope,
        int page,
        int size) {
        if (criteria == null || scope == null) {
            throw new IllegalArgumentException("criteria and scope are required");
        }
        return repository.findAll(
            specification(criteria, scope),
            PageRequest.of(page, size, sort()));
    }

    /** Counts before an export starts so the response can be bounded without loading all rows. */
    @Transactional(readOnly = true)
    long count(AdminAuditLogCriteria criteria, AuditLogScope scope) {
        return repository.count(specification(criteria, scope));
    }

    private Specification<AdminAuditLog> specification(
        AdminAuditLogCriteria criteria,
        AuditLogScope scope) {
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (scope.visibility() == Visibility.NONE) {
                predicates.add(builder.disjunction());
            } else if (scope.visibility() == Visibility.OWN) {
                predicates.add(builder.equal(root.get("adminUser").get("id"), scope.adminUserId()));
            }

            if (criteria.adminUserId() != null) {
                predicates.add(builder.equal(
                    root.get("adminUser").get("id"), criteria.adminUserId()));
            }
            if (criteria.action() != null) {
                predicates.add(builder.equal(root.get("action"), criteria.action()));
            }
            if (criteria.targetType() != null) {
                predicates.add(builder.equal(root.get("targetType"), criteria.targetType()));
            }
            if (criteria.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                    root.<Instant>get("createdAt"),
                    AnalyticsTimeZone.startOfDay(criteria.from())));
            }
            if (criteria.to() != null) {
                predicates.add(builder.lessThan(
                    root.<Instant>get("createdAt"),
                    AnalyticsTimeZone.endOfDayExclusive(criteria.to())));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static Sort sort() {
        return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }

    private boolean canSee(AdminAuditLog log, AuditLogScope scope) {
        if (scope.visibility() == Visibility.ALL) {
            return true;
        }
        return scope.visibility() == Visibility.OWN
            && log.getAdminUser() != null
            && scope.adminUserId().equals(log.getAdminUser().getId());
    }

    private AdminAuditLogSummaryResponse toSummary(AdminAuditLog log) {
        Actor actor = actor(log);
        return new AdminAuditLogSummaryResponse(
            log.getId(),
            log.getCreatedAt(),
            actor.id(),
            actor.name(),
            actor.email(),
            log.getAction(),
            log.getTargetType(),
            log.getTargetId(),
            log.getStatus(),
            log.getErrorCode(),
            log.getReason(),
            log.getRequestId(),
            log.getBatchId());
    }

    private AdminAuditLogDetailResponse toDetail(AdminAuditLog log) {
        Actor actor = actor(log);
        return new AdminAuditLogDetailResponse(
            log.getId(),
            log.getCreatedAt(),
            actor.id(),
            actor.name(),
            actor.email(),
            log.getAction(),
            log.getTargetType(),
            log.getTargetId(),
            parseJson(log.getBeforeJson()),
            parseJson(log.getAfterJson()),
            log.getReason(),
            log.getIpAddress(),
            log.getUserAgent(),
            log.getRequestId(),
            log.getBatchId(),
            log.getStatus(),
            log.getErrorCode());
    }

    private static Actor actor(AdminAuditLog log) {
        AdminUser admin = log.getAdminUser();
        User user = admin == null ? null : admin.getUser();
        return new Actor(
            admin == null ? null : admin.getId(),
            user == null ? null : user.getFullName(),
            user == null ? null : user.getEmail());
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException exception) {
            // Preserve legacy/corrupt snapshots as a JSON string instead of failing the whole detail response.
            return TextNode.valueOf(raw);
        }
    }

    private record Actor(UUID id, String name, String email) {
    }
}
