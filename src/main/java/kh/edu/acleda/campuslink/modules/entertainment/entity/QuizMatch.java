package kh.edu.acleda.campuslink.modules.entertainment.entity;

import jakarta.persistence.*;

import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizMatchStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "quiz_matches")
@AttributeOverride(name = "id", column = @Column(name = "match_id", nullable = false, updatable = false))
public class QuizMatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player1_id", nullable = false)
    private User player1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player2_id")
    private User player2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuizMatchStatus status;

    @Column(name = "player1_score", nullable = false)
    private int player1Score = 0;

    @Column(name = "player2_score", nullable = false)
    private int player2Score = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

}
