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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.finance.enums.AutoSaveType;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A rule that moves money into a saving goal automatically, once a month. The scheduler runs it
 * over the previous calendar month and advances {@link #nextRunDate} by a month each time.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "auto_save_rules")
@AttributeOverride(name = "id", column = @Column(name = "rule_id", nullable = false, updatable = false))
public class AutoSaveRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saving_goal_id", nullable = false)
    private SavingGoal savingGoal;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private AutoSaveType type;

    /** PERCENT_OF_INCOME only: the percent of last month's income to save (e.g. 10.00 = 10%). */
    @Column(name = "percent", precision = 5, scale = 2)
    private BigDecimal percent;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "next_run_date", nullable = false)
    private LocalDate nextRunDate;

    @Column(name = "last_run_date")
    private LocalDate lastRunDate;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "deleted_at")
    private Instant deletedAt;

}
