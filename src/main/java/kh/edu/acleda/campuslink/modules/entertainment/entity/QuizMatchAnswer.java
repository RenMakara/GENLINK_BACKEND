package kh.edu.acleda.campuslink.modules.entertainment.entity;

import jakarta.persistence.*;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "quiz_match_answers", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"match_id", "player_id", "question_id"})
})
@AttributeOverride(name = "id", column = @Column(name = "answer_id", nullable = false, updatable = false))
public class QuizMatchAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private QuizMatch match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private User player;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    private QuizOption option;

    @Column(name = "is_correct", nullable = false)
    private boolean isCorrect;


}
