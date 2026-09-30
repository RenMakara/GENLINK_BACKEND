package kh.edu.acleda.campuslink.modules.admin.user.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL read-only query for the admin user's university typeahead. */
@Repository
@Transactional(readOnly = true)
public class AdminUniversityFacetRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** Returns the most common live, non-blank university names matching the optional query. */
    public List<String> findUniversities(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        List<?> results = entityManager.createNativeQuery("""
            SELECT u.university_name
            FROM users u
            WHERE u.university_name IS NOT NULL
              AND btrim(u.university_name) <> ''
              AND u.deleted_at IS NULL
              AND u.account_status <> 'DELETED'
              AND (:query = '' OR u.university_name ILIKE CONCAT('%', :query, '%'))
            GROUP BY u.university_name
            ORDER BY COUNT(*) DESC, u.university_name ASC
            LIMIT 50
            """)
            .setParameter("query", normalizedQuery)
            .getResultList();

        return results.stream().map(String::valueOf).toList();
    }
}
