package kh.edu.acleda.campuslink.modules.finance.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Append-only log of every quiz submission, one row per attempt. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "finance_quiz_attempts")
@AttributeOverride(name = "id", column = @Column(name = "attempt_id", nullable = false, updatable = false))
public class FinanceQuizAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = true)
    private FinancialLesson lesson;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "passed", nullable = false)
    private boolean passed;

}
