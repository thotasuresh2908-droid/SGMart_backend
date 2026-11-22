package com.sgmart.config;

import com.sgmart.auth.SgMartOidcUserService;
import com.sgmart.auth.SgMartPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

/**
 * Who can call what.
 *  - /api/v1/admin/**  signed-in admins (owner-only endpoints add @PreAuthorize("hasRole('OWNER')"))
 *  - /api/v1/me/**     signed-in shoppers (Continue with Google)
 *  - other /api/v1/**  open to everyone; guest order endpoints check the tracking token themselves
 * Sessions use an HttpOnly cookie. CSRF uses the XSRF-TOKEN cookie, which axios sends automatically.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SgMartOidcUserService oidcUserService,
                                           SgMartProperties props) throws Exception {
        http
                .csrf(csrf -> csrf.spa())
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/me", "/api/v1/me/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/v1/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // WebSocket handshake; each subscription is checked by StompAuthInterceptor
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().denyAll())
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                        .successHandler(successHandler(props))
                        .failureHandler(failureHandler(props)))
                // API calls without a session get 401 JSON instead of a redirect to a login page
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        return http.build();
    }

    /** After Google sign-in: admins go to the order queue, shoppers back to the shop. */
    private AuthenticationSuccessHandler successHandler(SgMartProperties props) {
        return (request, response, authentication) -> {
            boolean admin = authentication.getPrincipal() instanceof SgMartPrincipal p && p.isAdmin();
            response.sendRedirect(props.publicUrl() + (admin ? "/admin/orders" : "/?signed_in=1"));
        };
    }

    /** Sign-in refused or cancelled: send the person back with a reason the web app can show. */
    private AuthenticationFailureHandler failureHandler(SgMartProperties props) {
        return (request, response, exception) -> {
            String code = exception instanceof OAuth2AuthenticationException oauth
                    ? oauth.getError().getErrorCode() : "failed";
            boolean adminFlow = request.getRequestURI().contains(SgMartOidcUserService.ADMIN_REGISTRATION);
            String target = adminFlow
                    ? "/admin/login?error=" + (SgMartOidcUserService.ERROR_NO_ADMIN_ACCESS.equals(code) ? "no_access" : "failed")
                    : "/?login_error=" + (SgMartOidcUserService.ERROR_EMAIL_NOT_VERIFIED.equals(code) ? "email_not_verified" : "failed");
            response.sendRedirect(props.publicUrl() + target);
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SgMartProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.corsAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Tracking-Token"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
