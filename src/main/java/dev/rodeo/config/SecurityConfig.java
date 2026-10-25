package dev.rodeo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    static final String GITHUB_WEBHOOK_PATH = "/webhooks/github";
    static final String GITLAB_WEBHOOK_PATH = "/webhooks/gitlab";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers(GITHUB_WEBHOOK_PATH, GITLAB_WEBHOOK_PATH))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(GITHUB_WEBHOOK_PATH, GITLAB_WEBHOOK_PATH).permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
