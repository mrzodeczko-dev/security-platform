package com.rzodeczko.infrastructure.security;


import com.rzodeczko.application.port.TokenVerificationPort;
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

/** Stateless JWT security configuration. CSRF disabled, no HTTP sessions. */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {
    private final TokenVerificationPort tokenVerificationPort;
    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .cors(c -> c.configurationSource(corsConfigurationSource()))

                .addFilterBefore(
                        new JwtAuthorizationFilter(tokenVerificationPort, objectMapper),
                        UsernamePasswordAuthenticationFilter.class
                )

                .authorizeHttpRequests(auth -> {
                    gatewayProperties.publicPaths().forEach(pattern -> {
                        var parts =  pattern.split(":", 2);
                        if (parts.length == 2) {
                            auth.requestMatchers(
                                    HttpMethod.valueOf(parts[0].toUpperCase()),
                                    parts[1]
                            ).permitAll();
                        }
                    });
                    auth.anyRequest().authenticated();
                })

                .exceptionHandling(e -> {
                    e.authenticationEntryPoint((req, res, ex) -> {
                        res.setStatus(HttpStatus.UNAUTHORIZED.value());
                        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        res.getWriter().write(objectMapper.writeValueAsString(
                                Map.of("error", "Unauthorized")
                        ));
                    });

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

    /** CORS source - allowed origins loaded from gateway properties. */
    private CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(gatewayProperties.cors().allowedOrigins());
        // Required for HttpOnly refresh-token cookie
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
