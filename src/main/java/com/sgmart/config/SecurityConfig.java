
package com.sgmart.config;

import com.sgmart.auth.SgMartOidcUserService;
import com.sgmart.auth.SgMartPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            SgMartOidcUserService oidcUserService,
            SgMartProperties props) throws Exception {

        http
                // Preserve SPA CSRF protection
                .csrf(csrf -> csrf.spa())

                .addFilterAfter(
                        new CsrfCookieFilter(),
                        BasicAuthenticationFilter.class
                )

                // Enable CORS for frontend requests
                .cors(Customizer.withDefaults())

                .authorizeHttpRequests(auth -> auth

                        // Allow CORS preflight requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public store APIs
                        .requestMatchers(
                                HttpMethod.GET,
                                "/categories",
                                "/categories/**",
                                "/products",
                                "/products/**",
                                "/store"
                        ).permitAll()

                        // Guest/customer session status
                        .requestMatchers(
                                HttpMethod.GET,
                                "/auth/session"
                        ).permitAll()

                        // Admin APIs
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")

                        // Customer APIs
                        .requestMatchers(
                                "/api/v1/me",
                                "/api/v1/me/**"
                        ).hasRole("CUSTOMER")

                        // Other versioned APIs
                        .requestMatchers("/api/v1/**").permitAll()

                        // Google OAuth endpoints
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()

                        // WebSocket
                        .requestMatchers("/ws", "/ws/**").permitAll()

                        // Health check
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        // Deny everything not explicitly configured
                        .anyRequest().denyAll()
                )

                // Google OAuth login
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo ->
                                userInfo.oidcUserService(oidcUserService)
                        )
                        .successHandler(successHandler(props))
                        .failureHandler(failureHandler(props))
                )

                // API requests without authentication return 401
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                        )
                )

                // Logout
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler(
                                new HttpStatusReturningLogoutSuccessHandler(
                                        HttpStatus.NO_CONTENT
                                )
                        )
                );

        return http.build();
    }

    /**
     * Admins go to admin orders.
     * Customers go back to the shop.
     */
    private AuthenticationSuccessHandler successHandler(
            SgMartProperties props) {

        return (request, response, authentication) -> {

            boolean admin =
                    authentication.getPrincipal() instanceof SgMartPrincipal p
                            && p.isAdmin();

            response.sendRedirect(
                    props.publicUrl()
                            + (admin
                            ? "/admin/orders"
                            : "/?signed_in=1")
            );
        };
    }

    /**
     * Handle Google authentication failures.
     */
    private AuthenticationFailureHandler failureHandler(
            SgMartProperties props) {

        return (request, response, exception) -> {

            String code =
                    exception instanceof OAuth2AuthenticationException oauth
                            ? oauth.getError().getErrorCode()
                            : "failed";

            boolean adminFlow = request.getRequestURI()
                    .contains(SgMartOidcUserService.ADMIN_REGISTRATION);

            String target = adminFlow
                    ? "/admin/login?error="
                    + (SgMartOidcUserService.ERROR_NO_ADMIN_ACCESS.equals(code)
                    ? "no_access" : "failed")

                    : "/?login_error="
                    + (SgMartOidcUserService.ERROR_EMAIL_NOT_VERIFIED.equals(code)
                    ? "email_not_verified" : "failed");

            response.sendRedirect(props.publicUrl() + target);
        };
    }

    /**
     * CORS configuration for the deployed React frontend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            SgMartProperties props) {

        CorsConfiguration config = new CorsConfiguration();

        // Must contain https://sg-mart-ui.vercel.app
        config.setAllowedOrigins(props.corsAllowedOrigins());

        config.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        config.setAllowedHeaders(List.of(
                "Content-Type",
                "Authorization",
                "Accept",
                "X-Requested-With",
                "X-XSRF-TOKEN",
                "X-Tracking-Token"
        ));

        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        // Important: apply CORS to root-level and /api endpoints
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
