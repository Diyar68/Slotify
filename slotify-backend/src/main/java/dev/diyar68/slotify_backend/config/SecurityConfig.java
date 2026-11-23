package dev.diyar68.slotify_backend.config;

import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public HttpSessionCsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(
        HttpSessionCsrfTokenRepository csrfTokens
    ) {
        return new CompositeSessionAuthenticationStrategy(
            List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokens)
            )
        );
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
        @Value("${slotify.frontend-origin:http://localhost:5173}") String frontendOrigin
    ) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(frontendOrigin));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        HttpSessionSecurityContextRepository contexts,
        HttpSessionCsrfTokenRepository csrfTokens,
        SessionAuthenticationStrategy sessions,
        UrlBasedCorsConfigurationSource cors
    ) throws Exception {
        var serviceEndpoints = PathPatternRequestMatcher.withDefaults().matcher("/api/services/**");
        var availabilityEndpoints = PathPatternRequestMatcher.withDefaults().matcher(
            "/api/availabilities/**"
        );
        var appointmentEndpoints = PathPatternRequestMatcher.withDefaults().matcher(
            "/api/appointments/**"
        );
        http.cors(config -> config.configurationSource(cors))
            .csrf(csrf ->
                csrf
                    .csrfTokenRepository(csrfTokens)
                    .ignoringRequestMatchers(
                        PathPatternRequestMatcher.withDefaults().matcher(
                            HttpMethod.POST,
                            "/api/public/{bookingSlug}/appointments"
                        )
                    )
                    .ignoringRequestMatchers("/api/auth/register", "/api/auth/registration-link")
            )
            .securityContext(context -> context.securityContextRepository(contexts))
            .sessionManagement(session -> session.sessionAuthenticationStrategy(sessions))
            .requestCache(cache -> cache.disable())
            .authorizeHttpRequests(auth ->
                auth
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/",
                        "/index.html",
                        "/assets/**",
                        "/favicon.svg",
                        "/login",
                        "/register",
                        "/register/complete",
                        "/dashboard",
                        "/dashboard/**",
                        "/book/*"
                    )
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/config")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/registration-link"
                    )
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/registration-token/validate")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/csrf")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/public/{bookingSlug}/slots")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/public/{bookingSlug}")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/public/{bookingSlug}/appointments")
                    .permitAll()
                    .requestMatchers("/api/services", "/api/services/**")
                    .hasRole("PROVIDER")
                    .requestMatchers("/api/availabilities", "/api/availabilities/**")
                    .hasRole("PROVIDER")
                    .requestMatchers("/api/appointments", "/api/appointments/**")
                    .hasRole("PROVIDER")
                    .requestMatchers("/api/auth/me")
                    .authenticated()
                    // All implemented API routes are explicitly secured above.
                    .anyRequest()
                    .denyAll()
            )
            .exceptionHandling(errors ->
                errors
                    .accessDeniedHandler((request, response, ex) -> {
                        var authentication = SecurityContextHolder.getContext().getAuthentication();
                        boolean anonymous =
                            authentication == null ||
                            authentication instanceof AnonymousAuthenticationToken;
                        boolean authenticationRequired =
                            anonymous &&
                            (serviceEndpoints.matches(request) ||
                                availabilityEndpoints.matches(request) ||
                                appointmentEndpoints.matches(request));
                        boolean protectedApi =
                            serviceEndpoints.matches(request) ||
                            availabilityEndpoints.matches(request) ||
                            appointmentEndpoints.matches(request) ||
                            request.getRequestURI().equals("/api/auth/me");
                        if (
                            !protectedApi &&
                            !(ex instanceof org.springframework.security.web.csrf.CsrfException)
                        ) {
                            response.sendError(404);
                            return;
                        }
                        response.setStatus(authenticationRequired ? 401 : 403);
                        response.setContentType("application/json");
                        response
                            .getWriter()
                            .write(
                                authenticationRequired
                                    ? "{\"status\":401,\"message\":\"Authentication required\"}"
                                    : "{\"status\":403,\"message\":\"Access denied or invalid CSRF token\"}"
                            );
                    })
                    .authenticationEntryPoint((request, response, ex) -> {
                        if (
                            !request.getRequestURI().equals("/api/auth/me") &&
                            !serviceEndpoints.matches(request) &&
                            !availabilityEndpoints.matches(request) &&
                            !appointmentEndpoints.matches(request)
                        ) {
                            response.sendError(404);
                            return;
                        }
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response
                            .getWriter()
                            .write("{\"status\":401,\"message\":\"Authentication required\"}");
                    })
            )
            .logout(logout ->
                logout
                    .logoutUrl("/api/auth/logout")
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler((request, response, authentication) ->
                        response.setStatus(204)
                    )
            )
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
