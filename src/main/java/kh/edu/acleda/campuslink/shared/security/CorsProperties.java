package kh.edu.acleda.campuslink.shared.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /** Origins allowed to call {@code /api/v1/admin/**} from a browser (SSOT §10.9). */
    private List<String> adminOrigins = List.of();

    public List<String> getAdminOrigins() {
        return adminOrigins;
    }

    public void setAdminOrigins(List<String> adminOrigins) {
        this.adminOrigins = adminOrigins == null ? List.of() : adminOrigins;
    }
}
