package kh.edu.acleda.campuslink.modules.user;

import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findFirstByFullNameIgnoreCaseAndDeletedAtIsNull(String fullName);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByPhoneNumber(String phoneNumber);

    /** Active, non-deleted users for bounded admin broadcasts. */
    List<User> findByAccountStatusAndDeletedAtIsNullOrderById(
        AccountStatus accountStatus, Pageable pageable);

    /**
     * Soft-deleted accounts whose grace period has run out, oldest first.
     *
     * <p>Paged rather than unbounded: the anonymisation job runs unattended, and a
     * single query that loads every expired account would be one bad day away from
     * an OOM on a 768 MB container.
     */
    List<User> findByAccountStatusAndDeletedAtBeforeOrderByDeletedAtAsc(
        AccountStatus accountStatus, Instant cutoff, Pageable pageable);

    default boolean existsByPhone(String phone) {
        return existsByPhoneNumber(phone);
    }

    /**
     * Case-insensitive, partial-match search across username, full name, and university.
     * Soft-deleted and banned accounts are excluded. Matches are ranked: exact username match
     * first, then prefix username match, then prefix name match, then alphabetical.
     */
    @Query(value = """
        SELECT u FROM User u
        WHERE u.deletedAt IS NULL
          AND (u.accountStatus IS NULL OR (u.accountStatus != kh.edu.acleda.campuslink.modules.user.entity.AccountStatus.DELETED
                                      AND u.accountStatus != kh.edu.acleda.campuslink.modules.user.entity.AccountStatus.BANNED))
          AND (
               LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR (u.universityName IS NOT NULL AND LOWER(u.universityName) LIKE LOWER(CONCAT('%', :query, '%')))
            OR (u.bio IS NOT NULL AND LOWER(u.bio) LIKE LOWER(CONCAT('%', :query, '%')))
          )
        ORDER BY
          CASE
            WHEN LOWER(u.username) = LOWER(:query) THEN 1
            WHEN LOWER(u.username) LIKE LOWER(CONCAT(:query, '%')) THEN 2
            WHEN LOWER(u.fullName) = LOWER(:query) THEN 3
            WHEN LOWER(u.fullName) LIKE LOWER(CONCAT(:query, '%')) THEN 4
            ELSE 5
          END,
          u.fullName ASC
        """,
        countQuery = """
        SELECT COUNT(u) FROM User u
        WHERE u.deletedAt IS NULL
          AND (u.accountStatus IS NULL OR (u.accountStatus != kh.edu.acleda.campuslink.modules.user.entity.AccountStatus.DELETED
                                      AND u.accountStatus != kh.edu.acleda.campuslink.modules.user.entity.AccountStatus.BANNED))
          AND (
               LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR (u.universityName IS NOT NULL AND LOWER(u.universityName) LIKE LOWER(CONCAT('%', :query, '%')))
            OR (u.bio IS NOT NULL AND LOWER(u.bio) LIKE LOWER(CONCAT('%', :query, '%')))
          )
        """)
    Page<User> searchUsers(@Param("query") String query, Pageable pageable);
}
