package kh.edu.acleda.campuslink.modules.user;

import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
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
    Optional<User> findByUsername(String username);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByPhoneNumber(String phoneNumber);

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
}
