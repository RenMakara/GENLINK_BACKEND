package kh.edu.acleda.campuslink.modules.admin.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a high-tier action that needs a fresh re-authentication (SSOT §10.7).
 *
 * <p>Delete a user, bulk ban, CSV export, role change, manual finance adjustment. Enforced by
 * {@link AdminSudoInterceptor}: without an open window the request is refused with
 * {@code ADMIN_SUDO_REQUIRED} before the handler runs.
 *
 * <p>This is the control that bounds the damage from an unattended, unlocked laptop. Holding a
 * valid admin token is not enough for the actions that cannot be undone - the person has to
 * still be there, and prove it within the last five minutes.
 *
 * <p>Declared as an annotation rather than a call inside each service so that adding a new
 * destructive endpoint and forgetting the check is visible in review: the annotation is either
 * on the method or it is not.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresSudo {
}
