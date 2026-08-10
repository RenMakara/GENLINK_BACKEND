package kh.edu.acleda.campuslink.shared.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * Origins allowed to call {@code /api/v1/admin/**} from a browser (SSOT §10.9).
     *
     * <p>Comma-separated, from {@code ADMIN_CORS_ORIGINS}. Full origins including scheme and
     * port - {@code https://admin.genlink.kh}, {@code http://localhost:3000}. <strong>No
     * wildcard, in any environment.</strong> A wildcard is survivable for a mobile-only bearer
     * API; it is not survivable for a browser-based admin portal, where any site the admin
     * visits could then script authenticated calls against it.
     *
     * <h2>Empty is the correct default</h2>
     *
     * Under the path-based routing chosen for the MVP (SSOT §1.7) the portal and the API share
     * one nginx origin, so requests are same-origin and CORS never applies - nothing needs
     * listing, and nothing breaks. The setting exists for the eventual subdomain split.
     *
     * <p>Empty therefore means "refuse every cross-origin admin request", which is the safe
     * reading. Defaulting to something permissive would leave a hole open for the case nobody
     * has configured yet.
     */
    private List<String> adminOrigins = List.of();

    public List<String> getAdminOrigins() {
        return adminOrigins;
    }

    public void setAdminOrigins(List<String> adminOrigins) {
        this.adminOrigins = adminOrigins == null ? List.of() : adminOrigins;
    }
}
