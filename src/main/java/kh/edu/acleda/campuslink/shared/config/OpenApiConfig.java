package kh.edu.acleda.campuslink.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI campuslinkOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("CampusLink KH API")
                .version("v1")
                .description("CampusLink KH modular monolith API"));
    }
}


