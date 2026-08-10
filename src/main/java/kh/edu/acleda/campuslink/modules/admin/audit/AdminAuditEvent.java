package kh.edu.acleda.campuslink.modules.admin.audit;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import lombok.Builder;

/**
 * One privileged action, as the caller describes it.
 *
 * <p>Carries only what the caller knows. The actor, IP address, user agent and correlation id
 * are filled in by {@link AdminAuditService} from the current request - asking every call site
 * to pass them would guarantee some of them forget.
 *
 * @param action      what was done, e.g. {@code USER_BAN}. Screaming snake case.
 * @param targetType  what it was done to, e.g. {@code USER}. Null for actions with no target.
 * @param targetId    the target's id.
 * @param beforeJson  state before the change, for update-shaped actions.
 * @param afterJson   state after.
 * @param reason      the admin's stated justification. Required by medium- and high-tier
 *                    actions, so it is normally present.
 * @param batchId     groups the rows of one bulk operation. Null for single actions.
 * @param status      whether the action actually succeeded.
 * @param errorCode   the code the caller returned. Only meaningful on failure.
 */
@Builder
public record AdminAuditEvent(
    String action,
    String targetType,
    UUID targetId,
    String beforeJson,
    String afterJson,
    String reason,
    UUID batchId,
    AuditStatus status,
    String errorCode
) {

    public boolean isFailure() {
        return status == AuditStatus.FAILURE;
    }
}
