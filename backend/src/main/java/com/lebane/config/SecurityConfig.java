package com.lebane.config;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lebane.exception.ApiError;
import com.lebane.exception.ErrorCode;
import com.lebane.logging.RequestContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Seguridad HTTP.
 *
 * <ul>
 *   <li>{@code /actuator/health/**} e {@code /actuator/info}: públicos (probes del orquestador). Sin detalles.</li>
 *   <li>Resto de endpoints de Actuator expuestos (metrics, prometheus): HTTP Basic, rol ACTUATOR.</li>
 *   <li>Endpoints no expuestos (env, beans, heapdump, ...): no existen (404).</li>
 *   <li>API REST: pública (el desafío no define autenticación de usuarios), stateless, sin sesión ni CSRF
 *       (no se usan cookies).</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    static final String ACTUATOR_ROLE = "ACTUATOR";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
            AuthenticationEntryPoint authenticationEntryPoint, AccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole(ACTUATOR_ROLE)
                        .anyRequest().permitAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService actuatorUserDetailsService(ActuatorSecurityProperties properties, PasswordEncoder encoder) {
        String password = properties.password();
        if (!properties.hasPassword()) {
            // Fail-safe: sin contraseña configurada, se usa una aleatoria que no se registra en ningún lado,
            // dejando metrics/prometheus efectivamente inaccesibles.
            password = UUID.randomUUID().toString() + UUID.randomUUID();
            log.warn("ACTUATOR_PASSWORD is not set: protected actuator endpoints are inaccessible");
        }
        return new InMemoryUserDetailsManager(User.withUsername(properties.username())
                .password(encoder.encode(password))
                .roles(ACTUATOR_ROLE)
                .build());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT,
                RequestContext.REQUEST_ID_HEADER, "traceparent"));
        config.setExposedHeaders(List.of(RequestContext.REQUEST_ID_HEADER, HttpHeaders.LOCATION));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"lebane-actuator\"");
            writeError(objectMapper, request, response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                    "Se requiere autenticación");
        };
    }

    @Bean
    AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> writeError(objectMapper, request, response,
                HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Acceso denegado");
    }

    private static void writeError(ObjectMapper objectMapper, HttpServletRequest request,
            HttpServletResponse response, HttpStatus status, ErrorCode code, String message) throws IOException {
        String requestId = (String) request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(status.value(), code, message, request.getRequestURI(), requestId));
    }
}
