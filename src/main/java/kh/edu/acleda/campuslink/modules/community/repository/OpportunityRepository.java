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
     * All opportunities, optionally filtered by type. Used when expired ones are
     * included.
     */
    @Query(value = "SELECT o FROM Opportunity o WHERE o.deletedAt IS NULL "
        + "AND (:type IS NULL OR o.type = :type)",
        countQuery = "SELECT COUNT(o) FROM Opportunity o WHERE o.deletedAt IS NULL "
            + "AND (:type IS NULL OR o.type = :type)")
    Page<Opportunity> browseAll(@Param("type") String type, Pageable pageable);

    /**
     * Active opportunities: deadline in the future, or open-ended (deadline null).
     *
     * <p>Split from browseAll on purpose. Putting activeFrom in a `:activeFrom IS NULL`
     * check made PostgreSQL fail with "could not determine data type of parameter" - a
     * bare date parameter in an IS NULL test has no type to infer, unlike a string
     * (which Postgres defaults to text). Here activeFrom only ever appears in
     * `o.deadline >= :activeFrom`, where the column gives it a type.
     */
    @Query(value = "SELECT o FROM Opportunity o WHERE o.deletedAt IS NULL "
        + "AND (:type IS NULL OR o.type = :type) "
        + "AND (o.deadline IS NULL OR o.deadline >= :activeFrom)",
        countQuery = "SELECT COUNT(o) FROM Opportunity o WHERE o.deletedAt IS NULL "
            + "AND (:type IS NULL OR o.type = :type) "
            + "AND (o.deadline IS NULL OR o.deadline >= :activeFrom)")
    Page<Opportunity> browseActive(@Param("type") String type,
                                   @Param("activeFrom") LocalDate activeFrom,
                                   Pageable pageable);
}
