package kh.edu.acleda.campuslink.modules.admin.user.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria.Direction;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria.LastActivePreset;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria.SortField;
import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** Criteria API query for the admin user directory. */
@Repository
public class AdminUserListRepository {

    private static final ZoneId PHNOM_PENH = ZoneId.of("Asia/Phnom_Penh");

    private final EntityManager entityManager;
    private final Clock clock;

    @Autowired
    public AdminUserListRepository(EntityManager entityManager) {
        this(entityManager, Clock.systemUTC());
    }

    AdminUserListRepository(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public Page<User> findPage(AdminUserListCriteria criteria, Pageable pageable) {
        Instant now = clock.instant();
        List<User> users = findUsers(criteria, Math.toIntExact(pageable.getOffset()),
            pageable.getPageSize(), now);
        long total = count(criteria, now);
        return new PageImpl<>(users, pageable, total);
    }

    /** Returns one bounded batch using the same filters and whitelist as the directory page. */
    public List<User> findBatch(AdminUserListCriteria criteria, int offset, int limit) {
        if (offset < 0 || limit < 1) {
            throw new IllegalArgumentException("offset must be non-negative and limit must be positive");
        }
        return findUsers(criteria, offset, limit, clock.instant());
    }

    /** Counts the complete filtered result before an export starts streaming. */
    public long count(AdminUserListCriteria criteria) {
        return count(criteria, clock.instant());
    }

    private List<User> findUsers(
        AdminUserListCriteria criteria,
        int offset,
        int limit,
        Instant now) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = builder.createQuery(User.class);
        Root<User> root = query.from(User.class);

        List<Predicate> predicates = predicates(builder, query, root, criteria, now);
        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(Predicate[]::new));
        }
        applyOrderBy(builder, query, root, criteria);

        TypedQuery<User> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(offset);
        typedQuery.setMaxResults(limit);
        return typedQuery.getResultList();
    }

    private long count(AdminUserListCriteria criteria, Instant now) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<User> countRoot = countQuery.from(User.class);
        List<Predicate> countPredicates = predicates(builder, countQuery, countRoot, criteria, now);
        countQuery.select(builder.count(countRoot));
        if (!countPredicates.isEmpty()) {
            countQuery.where(countPredicates.toArray(Predicate[]::new));
        }
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<Predicate> predicates(
        CriteriaBuilder builder,
        CriteriaQuery<?> query,
        Root<User> root,
        AdminUserListCriteria criteria,
        Instant now) {
        List<Predicate> predicates = new ArrayList<>();

        if (!criteria.includeDeleted()) {
            predicates.add(builder.isNull(root.get("deletedAt")));
            predicates.add(builder.notEqual(root.get("accountStatus"), AccountStatus.DELETED));
        }
        if (!criteria.statuses().isEmpty()) {
            predicates.add(root.get("accountStatus").in(criteria.statuses()));
        }
        if (criteria.emailVerified() != null) {
            predicates.add(builder.equal(root.get("emailVerified"), criteria.emailVerified()));
        }
        if (!criteria.workStatuses().isEmpty()) {
            predicates.add(root.get("workStatus").in(criteria.workStatuses()));
        }
        if (criteria.university() != null) {
            predicates.add(builder.equal(
                builder.lower(root.<String>get("universityName")),
                criteria.university().toLowerCase(java.util.Locale.ROOT)));
        }
        if (!criteria.genders().isEmpty()) {
            predicates.add(root.get("gender").in(criteria.genders()));
        }
        if (!criteria.languages().isEmpty()) {
            predicates.add(root.get("preferredLanguage").in(criteria.languages()));
        }
        if (!criteria.authProviders().isEmpty()) {
            predicates.add(root.get("authProvider").in(criteria.authProviders()));
        }
        if (criteria.levelMin() != null) {
            predicates.add(builder.greaterThanOrEqualTo(
                root.<Integer>get("level"), criteria.levelMin()));
        }
        if (criteria.levelMax() != null) {
            predicates.add(builder.lessThanOrEqualTo(root.<Integer>get("level"), criteria.levelMax()));
        }
        if (criteria.xpMin() != null) {
            predicates.add(builder.greaterThanOrEqualTo(
                root.<Integer>get("totalXp"), criteria.xpMin()));
        }
        if (criteria.xpMax() != null) {
            predicates.add(builder.lessThanOrEqualTo(root.<Integer>get("totalXp"), criteria.xpMax()));
        }
        if (criteria.createdFrom() != null) {
            Instant from = criteria.createdFrom().atStartOfDay(PHNOM_PENH).toInstant();
            predicates.add(builder.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
        }
        if (criteria.createdTo() != null) {
            Instant exclusiveTo = criteria.createdTo().plusDays(1).atStartOfDay(PHNOM_PENH).toInstant();
            predicates.add(builder.lessThan(root.<Instant>get("createdAt"), exclusiveTo));
        }
        if (criteria.banned()) {
            predicates.add(builder.equal(root.get("accountStatus"), AccountStatus.BANNED));
        }
        addSearchPredicate(builder, root, criteria, predicates);
        addLastActivePredicate(builder, query, root, criteria, now, predicates);

        return predicates;
    }

    private void addSearchPredicate(
        CriteriaBuilder builder,
        Root<User> root,
        AdminUserListCriteria criteria,
        List<Predicate> predicates) {
        if (criteria.search() == null) {
            return;
        }
        if (criteria.searchUuid() != null) {
            predicates.add(builder.equal(root.get("id"), criteria.searchUuid()));
            return;
        }

        String pattern = "%" + criteria.search().toLowerCase(java.util.Locale.ROOT) + "%";
        predicates.add(builder.or(
            builder.like(builder.lower(root.<String>get("email")), pattern),
            builder.like(builder.lower(root.<String>get("username")), pattern),
            builder.like(builder.lower(root.<String>get("fullName")), pattern),
            builder.like(builder.lower(root.<String>get("phoneNumber")), pattern)
        ));
    }

    private void addLastActivePredicate(
        CriteriaBuilder builder,
        CriteriaQuery<?> query,
        Root<User> root,
        AdminUserListCriteria criteria,
        Instant now,
        List<Predicate> predicates) {
        LastActivePreset preset = criteria.lastActive();
        if (preset == null) {
            return;
        }

        Expression<Instant> lastActive = lastActiveExpression(builder, query, root);
        switch (preset) {
            case WITHIN_24_HOURS -> predicates.add(
                builder.greaterThanOrEqualTo(lastActive, now.minusSeconds(24 * 60 * 60L)));
            case WITHIN_7_DAYS -> predicates.add(
                builder.greaterThanOrEqualTo(lastActive, now.minusSeconds(7 * 24 * 60 * 60L)));
            case WITHIN_30_DAYS -> predicates.add(
                builder.greaterThanOrEqualTo(lastActive, now.minusSeconds(30 * 24 * 60 * 60L)));
            case OVER_90_DAYS -> predicates.add(
                builder.lessThan(lastActive, now.minusSeconds(90 * 24 * 60 * 60L)));
            case NEVER -> predicates.add(builder.isNull(lastActive));
        }
    }

    private void applyOrderBy(
        CriteriaBuilder builder,
        CriteriaQuery<User> query,
        Root<User> root,
        AdminUserListCriteria criteria) {
        List<Order> orders = new ArrayList<>();
        for (AdminUserListCriteria.SortOrder sortOrder : criteria.sortOrders()) {
            Expression<?> expression = sortOrder.field() == SortField.LAST_ACTIVE_AT
                ? lastActiveExpression(builder, query, root)
                : root.get(sortOrder.field().entityAttribute());
            orders.add(sortOrder.direction() == Direction.ASC
                ? builder.asc(expression)
                : builder.desc(expression));
        }
        // Stable pagination when multiple users share a sort value.
        orders.add(builder.asc(root.get("id")));
        query.orderBy(orders);
    }

    private Expression<Instant> lastActiveExpression(
        CriteriaBuilder builder,
        CriteriaQuery<?> query,
        Root<User> root) {
        Subquery<Instant> subquery = query.subquery(Instant.class);
        Root<RefreshToken> refreshToken = subquery.from(RefreshToken.class);
        subquery.select(builder.greatest(refreshToken.<Instant>get("lastUsedAt")));
        subquery.where(builder.equal(refreshToken.get("user"), root));
        return subquery;
    }
}
