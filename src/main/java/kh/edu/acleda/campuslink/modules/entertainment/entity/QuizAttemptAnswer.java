package kh.edu.acleda.campuslink.modules.entertainment.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizAttempt;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizOption;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizQuestion;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "quiz_attempt_answers")
@AttributeOverride(name = "id", column = @Column(name = "answer_id", nullable = false, updatable = false))
public class QuizAttemptAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = true)
    private QuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = true)
    private QuizQuestion question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id", nullable = true)
    private QuizOption selectedOption;

    @Column(name = "correct", nullable = false)
    private Boolean correct;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;

}
