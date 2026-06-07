package kh.edu.acleda.campuslink.modules.finance.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kh.edu.acleda.campuslink.modules.finance.entity.FinancialLesson;
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
@Table(name = "financial_quiz_questions")
@AttributeOverride(name = "id", column = @Column(name = "question_id", nullable = false, updatable = false))
public class FinancialQuizQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = true)
    private FinancialLesson lesson;

    @Column(name = "question_text_en", nullable = false, columnDefinition = "TEXT")
    private String questionTextEn;

    @Column(name = "question_text_km", columnDefinition = "TEXT")
    private String questionTextKm;

    @Column(name = "option_a_en", nullable = false, columnDefinition = "TEXT")
    private String optionAEn;

    @Column(name = "option_a_km", columnDefinition = "TEXT")
    private String optionAKm;

    @Column(name = "option_b_en", nullable = false, columnDefinition = "TEXT")
    private String optionBEn;

    @Column(name = "option_b_km", columnDefinition = "TEXT")
    private String optionBKm;

    @Column(name = "option_c_en", columnDefinition = "TEXT")
    private String optionCEn;

    @Column(name = "option_c_km", columnDefinition = "TEXT")
    private String optionCKm;

    @Column(name = "option_d_en", columnDefinition = "TEXT")
    private String optionDEn;

    @Column(name = "option_d_km", columnDefinition = "TEXT")
    private String optionDKm;

    @Column(name = "correct_answer", nullable = false, length = 10)
    private String correctAnswer;

    @Column(name = "explanation_en", columnDefinition = "TEXT")
    private String explanationEn;

    @Column(name = "explanation_km", columnDefinition = "TEXT")
    private String explanationKm;

}
