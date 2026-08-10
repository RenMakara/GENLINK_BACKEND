package kh.edu.acleda.campuslink.modules.admin.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public class AdminBootstrapProperties {

    /**
     * Email of an existing, verified user to promote to {@code SUPER_ADMIN} on startup
     * (SSOT §13.3).
     *
     * <p>Blank by default, which is the normal state of every environment. Set it once, start
     * the application, confirm the promotion in the logs, then <strong>remove it</strong>.
     *
     * <p>Leaving it set is not a security hole - the runner refuses to act while any active
     * {@code SUPER_ADMIN} exists - but it is a loaded gun pointed at the day someone
     * deactivates the last one.
     */
    private String bootstrapEmail;

    public String getBootstrapEmail() {
        return bootstrapEmail;
    }

    public void setBootstrapEmail(String bootstrapEmail) {
        this.bootstrapEmail = bootstrapEmail;
    }
}
