package kh.edu.acleda.campuslink.modules.finance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.FinancialLesson;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialLessonRepository extends JpaRepository<FinancialLesson, UUID> {

    Optional<FinancialLesson> findByIdAndDeletedAtIsNull(UUID id);

    List<FinancialLesson> findByDeletedAtIsNullOrderByTopicAscCreatedAtAsc();

    List<FinancialLesson> findByTopicAndDeletedAtIsNullOrderByCreatedAtAsc(String topic);

    /** First live lesson tagged with this spending category (case-insensitive) - insight matching. */
    Optional<FinancialLesson> findFirstByRelatedCategoryIgnoreCaseAndDeletedAtIsNull(String relatedCategory);

    /** First live lesson for a topic - the fallback when no category-specific lesson exists. */
    Optional<FinancialLesson> findFirstByTopicAndDeletedAtIsNullOrderByCreatedAtAsc(String topic);
}
