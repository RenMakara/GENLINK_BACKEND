package kh.edu.acleda.campuslink.modules.admin.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public class AdminBootstrapProperties {

    /** Email of an existing, verified user to promote to {@code SUPER_ADMIN} on startup (SSOT §13.3). */
    private String bootstrapEmail;

    public String getBootstrapEmail() {
        return bootstrapEmail;
    }

    public void setBootstrapEmail(String bootstrapEmail) {
        this.bootstrapEmail = bootstrapEmail;
    }
}
