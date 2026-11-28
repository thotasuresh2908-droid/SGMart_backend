
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.function.Supplier;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            SgMartOidcUserService oidcUserService,
            SgMartProperties props
    ) throws Exception {

        CookieCsrfTokenRepository csrfRepository =
                CookieCsrfTokenRepository.withHttpOnlyFalse();

        csrfRepository.setCookieName("XSRF-TOKEN");
        csrfRepository.setHeaderName("X-XSRF-TOKEN");
        csrfRepository.setCookiePath("/");

        // HTTPS cross-origin cookie configuration.
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .secure(true)
                .httpOnly(false)
                .path("/")
                .sameSite("None")
        );

        http
                // SPA CSRF protection.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(
                                spaCsrfTokenRequestHandler()
                        )
                )

                // Generate and expose the CSRF token.
                .addFilterAfter(
                        new CsrfCookieFilter(),
                        BasicAuthenticationFilter.class
                )

                // CORS.
                .cors(Customizer.withDefaults())

                // Authorization.
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()

                        // Public store APIs.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/categories",
                                "/categories/**",
                                "/products",
                                "/products/**",
                                "/store",
                                "/api/v1/categories",
                                "/api/v1/categories/**",
                                "/api/v1/products",
                                "/api/v1/products/**",
                                "/api/v1/store"
                        ).permitAll()

                        // Guest session status.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/auth/session"
                        ).permitAll()

                        // Admin APIs.
                        .requestMatchers(
                                "/api/v1/admin/**"
                        ).hasRole("ADMIN")

                        // Customer profile APIs.
                        .requestMatchers(
                                "/api/v1/me",
                                "/api/v1/me/**"
                        ).hasRole("CUSTOMER")

                        // Other versioned APIs.
                        .requestMatchers(
                                "/api/v1/**"
                        ).permitAll()

                        // Google OAuth.
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()

                        // WebSocket.
                        .requestMatchers(
                                "/ws",
                                "/ws/**"
                        ).permitAll()

                        // Health check.
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        .anyRequest().denyAll()
                )

                // Google OAuth login.
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo ->
                                userInfo.oidcUserService(oidcUserService)
                        )
                        .successHandler(successHandler(props))
                        .failureHandler(failureHandler(props))
                )

                // API authentication failure.
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(
                                new HttpStatusEntryPoint(
                                        HttpStatus.UNAUTHORIZED
                                )
                        )
                )

                // Logout.
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
     * SPA CSRF handler.
     *
     * Supports the plain token sent in X-XSRF-TOKEN
     * while retaining Spring Security's BREACH protection
     * for request attributes.
     */
    private CsrfTokenRequestHandler spaCsrfTokenRequestHandler() {

        CsrfTokenRequestAttributeHandler plain =
                new CsrfTokenRequestAttributeHandler();

        XorCsrfTokenRequestAttributeHandler xor =
                new XorCsrfTokenRequestAttributeHandler();

        return new CsrfTokenRequestHandler() {

            @Override
            public void handle(
                    jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response,
                    Supplier<org.springframework.security.web.csrf.CsrfToken> deferredToken
            ) {
                xor.handle(request, response, deferredToken);

                // Force token generation so the cookie is written.
                deferredToken.get().getToken();
            }

            @Override
            public String resolveCsrfTokenValue(
                    jakarta.servlet.http.HttpServletRequest request,
                    org.springframework.security.web.csrf.CsrfToken csrfToken
            ) {
                String headerValue =
                        request.getHeader(csrfToken.getHeaderName());

                if (headerValue != null && !headerValue.isBlank()) {
                    return plain.resolveCsrfTokenValue(
                            request,
                            csrfToken
                    );
                }

                return xor.resolveCsrfTokenValue(
                        request,
                        csrfToken
                );
            }
        };
    }

    /**
     * After Google sign-in:
     * Admin -> Admin orders.
     * Customer -> Store homepage.
     */
    private AuthenticationSuccessHandler successHandler(
            SgMartProperties props
    ) {

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
     * Google login failure handler.
     */
    private AuthenticationFailureHandler failureHandler(
            SgMartProperties props
    ) {

        return (request, response, exception) -> {

            String code =
                    exception instanceof OAuth2AuthenticationException oauth
                            ? oauth.getError().getErrorCode()
                            : "failed";

            boolean adminFlow = request.getRequestURI()
                    .contains(
                            SgMartOidcUserService.ADMIN_REGISTRATION
                    );

            String target = adminFlow
                    ? "/admin/login?error="
                    + (
                            SgMartOidcUserService.ERROR_NO_ADMIN_ACCESS
                                    .equals(code)
                                    ? "no_access"
                                    : "failed"
                    )
                    : "/?login_error="
                    + (
                            SgMartOidcUserService.ERROR_EMAIL_NOT_VERIFIED
                                    .equals(code)
                                    ? "email_not_verified"
                                    : "failed"
                    );

            response.sendRedirect(props.publicUrl() + target);
        };
    }

    /**
     * CORS configuration.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            SgMartProperties props
    ) {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(
                props.corsAllowedOrigins()
        );

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

        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
