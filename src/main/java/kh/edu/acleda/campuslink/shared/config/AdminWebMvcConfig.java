package kh.edu.acleda.campuslink.shared.config;

import kh.edu.acleda.campuslink.modules.admin.auth.AdminSudoInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the sudo guard (ADM-025). */
@Configuration
public class AdminWebMvcConfig implements WebMvcConfigurer {

    private final AdminSudoInterceptor adminSudoInterceptor;

    public AdminWebMvcConfig(AdminSudoInterceptor adminSudoInterceptor) {
        this.adminSudoInterceptor = adminSudoInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminSudoInterceptor).addPathPatterns("/api/v1/admin/**");
    }
}
