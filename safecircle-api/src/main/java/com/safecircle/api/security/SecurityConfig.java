package com.safecircle.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SPRING SECURITY CONCEPT:
 *
 * Spring Security intercepts every HTTP request before it reaches your controller.
 * It runs a chain of "filters" — each filter does one job (check JWT, check roles, etc.).
 *
 * SecurityFilterChain defines the rules:
 *  - Which endpoints are public (no auth needed)
 *  - Which endpoints require authentication
 *  - What kind of authentication (JWT, not sessions)
 *
 * STATELESS SESSION CONCEPT:
 * Traditional web apps use server-side sessions — the server remembers who you are
 * between requests by storing session data in memory (or Redis).
 * REST APIs use stateless auth — every request carries its own proof of identity
 * (the JWT token). The server verifies the token on every request and never
 * stores session state. This is why SessionCreationPolicy.STATELESS is set below.
 *
 * CSRF CONCEPT:
 * CSRF (Cross-Site Request Forgery) protection is needed for cookie-based auth
 * because browsers automatically send cookies with every request.
 * Since we use JWT in the Authorization header (not cookies), browsers don't
 * automatically attach it — so CSRF is not a risk and we disable it.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CognitoJwtFilter cognitoJwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — we use stateless JWT, not cookies
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless — no HTTP session, no cookies
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no JWT required
                .requestMatchers(
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/actuator/health"      // ECS health check — must be public
                ).permitAll()
                // Everything else requires a valid JWT
                .anyRequest().authenticated()
            )

            // Add our JWT validation filter BEFORE Spring's default auth filter.
            // Our filter extracts the user from the JWT; Spring's filter then
            // checks if that user is allowed to access the requested endpoint.
            .addFilterBefore(cognitoJwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}