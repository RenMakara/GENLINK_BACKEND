package co.istad.makara.ebanking.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {


    @Bean
    public SecurityWebFilterChain webSecurity(ServerHttpSecurity http){


        http.authorizeExchange(exchanges -> exchanges
                .pathMatchers("/").permitAll()
                .anyExchange().authenticated()
        );
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        http.formLogin(ServerHttpSecurity.FormLoginSpec::disable);
//        http.logout(ServerHttpSecurity.LogoutSpec::disable);
        http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);

        http.oauth2Login(Customizer.withDefaults());

        return http.build();
    }
}
