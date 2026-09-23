package kh.edu.acleda.campuslink.modules.admin.analytics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL read-only aggregate implementation for user activity counts. */
@Repository
@Transactional(readOnly = true)
public class AdminUserActivityQueryRepositoryImpl implements AdminUserActivityQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public UserActivityCounts findByUserId(UUID userId) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
            SELECT
                CASE WHEN EXISTS (
                    SELECT 1 FROM users u WHERE u.user_id = :userId
                ) THEN 1 ELSE 0 END,
                (
                    SELECT COUNT(*)
                    FROM posts p
                    WHERE p.user_id = :userId
                      AND p.deleted_at IS NULL
                      AND (
                          p.community_id IS NULL
                          OR EXISTS (
                              SELECT 1
                              FROM communities c
                              WHERE c.community_id = p.community_id
                                AND c.deleted_at IS NULL
                          )
                      )
                ),
                (
                    SELECT COUNT(*)
                    FROM post_comments pc
                    JOIN posts p ON p.post_id = pc.post_id
                    WHERE pc.user_id = :userId
                      AND pc.deleted_at IS NULL
                      AND p.deleted_at IS NULL
                      AND (
                          p.community_id IS NULL
                          OR EXISTS (
                              SELECT 1
                              FROM communities c
                              WHERE c.community_id = p.community_id
                                AND c.deleted_at IS NULL
                          )
                      )
                ) + (
                    SELECT COUNT(*)
                    FROM forum_comments fc
                    JOIN forum_discussions fd ON fd.discussion_id = fc.discussion_id
                    WHERE fc.user_id = :userId
                      AND fc.deleted_at IS NULL
                      AND fd.deleted_at IS NULL
                ),
                (
                    SELECT COUNT(*)
                    FROM community_members cm
                    JOIN communities c ON c.community_id = cm.community_id
                    WHERE cm.user_id = :userId
                      AND cm.status = 'ACTIVE'
                      AND c.deleted_at IS NULL
                ),
                (
                    SELECT COUNT(*)
                    FROM event_rsvps er
                    JOIN events e ON e.event_id = er.event_id
                    WHERE er.user_id = :userId
                      AND er.status IN ('JOINED', 'ATTENDED')
                      AND e.deleted_at IS NULL
                ),
                (
                    SELECT COUNT(*)
                    FROM post_reports pr
                    JOIN posts p ON p.post_id = pr.post_id
                    WHERE pr.user_id = :userId
                      AND p.deleted_at IS NULL
                      AND (
                          p.community_id IS NULL
                          OR EXISTS (
                              SELECT 1
                              FROM communities c
                              WHERE c.community_id = p.community_id
                                AND c.deleted_at IS NULL
                          )
                      )
                ) + (
                    SELECT COUNT(*)
                    FROM discussion_reports dr
                    JOIN forum_discussions fd ON fd.discussion_id = dr.discussion_id
                    WHERE dr.user_id = :userId
                      AND fd.deleted_at IS NULL
                ),
                (
                    SELECT COUNT(*)
                    FROM post_reports pr
                    JOIN posts p ON p.post_id = pr.post_id
                    WHERE p.user_id = :userId
                      AND p.deleted_at IS NULL
                      AND (
                          p.community_id IS NULL
                          OR EXISTS (
                              SELECT 1
                              FROM communities c
                              WHERE c.community_id = p.community_id
                                AND c.deleted_at IS NULL
                          )
                      )
                ) + (
                    SELECT COUNT(*)
                    FROM discussion_reports dr
                    JOIN forum_discussions fd ON fd.discussion_id = dr.discussion_id
                    WHERE fd.user_id = :userId
                      AND fd.deleted_at IS NULL
                )
            """)
            .setParameter("userId", userId)
            .getSingleResult();

        return new UserActivityCounts(
            number(row[0]) == 1,
            number(row[1]),
            number(row[2]),
            number(row[3]),
            number(row[4]),
            number(row[5]),
            number(row[6])
        );
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
