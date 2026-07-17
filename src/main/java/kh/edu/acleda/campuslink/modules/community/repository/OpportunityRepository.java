package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.Opportunity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface OpportunityRepository extends JpaRepository<Opportunity, UUID> {

    Optional<Opportunity> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Browse list. Filters by type when given; activeFrom hides opportunities whose
     * deadline has passed - an open-ended opportunity (deadline null) always shows.
     */
    @Query(value = "SELECT o FROM Opportunity o WHERE o.deletedAt IS NULL "
        + "AND (:type IS NULL OR o.type = :type) "
        + "AND (:activeFrom IS NULL OR o.deadline IS NULL OR o.deadline >= :activeFrom)",
        countQuery = "SELECT COUNT(o) FROM Opportunity o WHERE o.deletedAt IS NULL "
            + "AND (:type IS NULL OR o.type = :type) "
            + "AND (:activeFrom IS NULL OR o.deadline IS NULL OR o.deadline >= :activeFrom)")
    Page<Opportunity> browse(@Param("type") String type,
                             @Param("activeFrom") LocalDate activeFrom,
                             Pageable pageable);
}
