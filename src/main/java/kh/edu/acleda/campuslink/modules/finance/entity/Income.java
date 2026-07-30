package kh.edu.acleda.campuslink.modules.finance.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Money in - allowance, part-time, scholarship. The inflow twin of {@link Expense}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "incomes")
@AttributeOverride(name = "id", column = @Column(name = "income_id", nullable = false, updatable = false))
public class Income extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Free text - ALLOWANCE, PART_TIME, SCHOLARSHIP, GIFT, OTHER - the vocabulary is not settled. */
    @Column(name = "source", length = 80)
    private String source;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "income_date", nullable = false)
    private LocalDate incomeDate;

    @Column(name = "deleted_at")
    private Instant deletedAt;

}
