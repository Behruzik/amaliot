package com.example.amaliyot.security;

import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.repository.UserAccountRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    UserDetailsService userDetailsService(UserAccountRepository users) {
        return identifier -> {
            String normalized = com.example.amaliyot.service.AuthService.normalizeLoginIdentifier(identifier);
            var account = normalized.indexOf('@') >= 0
                    ? users.findByEmailIgnoreCase(normalized)
                    : users.findByPhone(normalized);
            return account.filter(UserAccount::isActive)
                .map(user -> User.withUsername(user.getEmail() == null ? user.getPhone() : user.getEmail()).password(user.getPasswordHash())
                        .roles(user.getRole().name()).build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtFilter,
            @Value("${security.auth-rate-limit.max-requests:20}") int authMaxRequests,
            @Value("${security.auth-rate-limit.window-seconds:60}") int authWindowSeconds,
            @Value("${security.auth-rate-limit.max-clients:10000}") int authMaxClients) throws Exception {
        // Construct in the security chain instead of exposing a servlet Filter
        // bean, which would also be registered separately by Spring Boot.
        AuthRateLimitFilter authRateLimit = new AuthRateLimitFilter(authMaxRequests, authWindowSeconds, authMaxClients, Clock.systemUTC());
        return http.csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/password/forgot",
                                "/api/auth/password/reset", "/actuator/health", "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**", "/api/professions/**", "/api/locations/**", "/api/content/**",
                                "/api/certificates/verify/**", "/api/support/requests/contact", "/uploads/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) -> SecurityErrorWriter.write(request, response, 401, "UNAUTHORIZED", "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> SecurityErrorWriter.write(request, response, 403, "FORBIDDEN", "You do not have permission to perform this action")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(authRateLimit, JwtAuthFilter.class)
                .build();
    }
}
