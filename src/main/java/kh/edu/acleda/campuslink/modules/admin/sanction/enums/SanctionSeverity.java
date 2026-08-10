package kh.edu.acleda.campuslink.modules.admin.sanction.enums;

/**
 * How serious an enforcement action is, independent of its {@link SanctionType}.
 *
 * <p>Primarily meaningful on warnings, where it is the difference between a nudge and a final
 * notice. Nullable on the entity: a ban has a reason, not a severity.
 *
 * <p>Deliberately NOT wired to automatic escalation. Whether N warnings should auto-suspend is
 * a policy decision (SSOT Appendix B), and automated punishment is out of MVP scope.
 */
public enum SanctionSeverity {
    LOW,
    MEDIUM,
    HIGH
}
