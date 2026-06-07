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
import kh.edu.acleda.campuslink.modules.entertainment.entity.Quiz;
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
@Table(name = "quiz_questions")
@AttributeOverride(name = "id", column = @Column(name = "question_id", nullable = false, updatable = false))
public class QuizQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = true)
    private Quiz quiz;

    @Column(name = "question_text_en", nullable = false, columnDefinition = "TEXT")
    private String questionTextEn;

    @Column(name = "question_text_km", columnDefinition = "TEXT")
    private String questionTextKm;

}
