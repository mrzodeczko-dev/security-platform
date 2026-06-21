package com.rzodeczko.infrastructure.security;

import com.rzodeczko.application.port.out.TokenVerificationPort;
import com.rzodeczko.infrastructure.configuration.properties.GatewayProperties;
import com.rzodeczko.infrastructure.security.filter.JwtAuthorizationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
/**
 * Security configuration for JWT-based, stateless authentication.
 *
 * <p>@EnableWebSecurity is present for clarity; Spring Boot will enable web
 * security automatically when Spring Security is on the classpath.
 */
public class SecurityConfiguration {
    private final TokenVerificationPort tokenVerificationPort;
    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;

    /**
     * Configure the security filter chain.
     *
     * <p>Key decisions:
     * - CSRF is disabled because authentication is stateless and uses JWT in the Authorization header.
     * - Session management is STATELESS; JWT must be provided on every request.
     * - JwtAuthorizationFilter is registered before UsernamePasswordAuthenticationFilter to populate Authentication.
     * - Public, admin and user path patterns are read from properties and applied here.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Disable CSRF - JWT in Authorization header makes CSRF protection unnecessary
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session management - each request must include a valid JWT
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .cors(c -> c.configurationSource(corsConfigurationSource()))

                // Register JWT filter before UsernamePasswordAuthenticationFilter so Authentication is set early
                // Note: filter is created with new to avoid automatic registration outside the security chain
                .addFilterBefore(
                        new JwtAuthorizationFilter(tokenVerificationPort, objectMapper),
                        UsernamePasswordAuthenticationFilter.class
                )

                // Apply path-based rules from properties: publicPaths, adminPaths, userPaths
                // Patterns use format METHOD:/path and are matched with both method and path
                .authorizeHttpRequests(auth -> {

                    // PUBLIC
                    if (gatewayProperties.publicPaths() != null) {
                        gatewayProperties.publicPaths().forEach(pattern -> {
                            var parts = pattern.split(":", 2);
                            if (parts.length == 2) {
                                auth.requestMatchers(
                                        HttpMethod.valueOf(parts[0].toUpperCase()),
                                        parts[1]
                                ).permitAll();
                            }
                        });
                    }

                    // ADMIN
                    if (gatewayProperties.adminPaths() != null) {
                        gatewayProperties.adminPaths().forEach(pattern -> {
                            var parts = pattern.split(":", 2);
                            if (parts.length == 2) {
                                auth.requestMatchers(
                                                HttpMethod.valueOf(parts[0].toUpperCase()),
                                                parts[1])
                                        .hasAuthority("ROLE_ADMIN");
                            }
                        });
                    }

                    // USER
                    if (gatewayProperties.userPaths() != null) {
                        gatewayProperties.userPaths().forEach(pattern -> {
                            var parts = pattern.split(":", 2);
                            if (parts.length == 2) {
                                auth.requestMatchers(
                                                HttpMethod.valueOf(parts[0].toUpperCase()),
                                                parts[1])
                                        .hasAnyAuthority("ROLE_USER", "ROLE_ADMIN");
                            }
                        });
                    }

                    auth.anyRequest().authenticated();
                })

                .exceptionHandling(e -> {
                    // No token - return 401 JSON
                    e.authenticationEntryPoint((req, res, ex) -> {
                        res.setStatus(HttpStatus.UNAUTHORIZED.value());
                        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        res.getWriter().write(objectMapper.writeValueAsString(
                                Map.of("error", "Unauthorized")
                        ));
                    });

                    // Authenticated but lacking permission - return 403 JSON
                    e.accessDeniedHandler((req, res, ex) -> {
                        res.setStatus(HttpStatus.FORBIDDEN.value());
                        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        res.getWriter().write(objectMapper.writeValueAsString(
                                Map.of("error", "Forbidden")
                        ));
                    });
                })
                .build();
    }

    /**
     * Configure CORS for browser-based clients.
     *
     * <p>CORS is enforced by browsers only. This method builds a CorsConfigurationSource
     * from application properties to answer preflight requests and allow specified origins.
     */
    private CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(gatewayProperties.cors().allowedOrigins());
        // Wymagane dla HttpOnly refresh-token cookie.
        config.setAllowCredentials(true);
        config.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.CACHE_CONTROL
        ));
        config.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.OPTIONS.name()
        ));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
