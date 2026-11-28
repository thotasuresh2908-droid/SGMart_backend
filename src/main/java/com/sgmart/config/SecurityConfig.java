package com.sgmart.config;

import com.sgmart.auth.SgMartOidcUserService;
import com.sgmart.auth.SgMartPrincipal;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Who may call what.
 *
 * Access levels:
 *   PUBLIC    store, catalog, product photos, session check, CSRF token, guest checkout and
 *             order tracking (guests prove access to an order with its X-Tracking-Token).
 *   CUSTOMER  /api/v1/me/** (signed in with Google as a shopper).
 *   ADMIN     /api/v1/admin/** (OWNER-only actions are guarded again with @PreAuthorize).
 *   Everything else is denied. New endpoints are NOT public until listed here.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            SgMartOidcUserService oidcUserService,
            SgMartProperties props
    ) throws Exception {

        CookieCsrfTokenRepository csrfRepository =
                CookieCsrfTokenRepository.withHttpOnlyFalse();

        csrfRepository.setCookieName(CSRF_COOKIE);
        csrfRepository.setHeaderName(CSRF_HEADER);
        csrfRepository.setCookiePath("/");

        // The web app (Vercel) and the API (Render) are different sites, so the
        // cookie must be Secure + SameSite=None to be sent on API calls.
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .secure(true)
                .httpOnly(false)
                .path("/")
                .sameSite("None")
        );

        http
                // CSRF: the browser sends the token back in the X-XSRF-TOKEN header.
                // It gets the token from GET /api/v1/auth/csrf (works cross-site) or,
                // when the web app and API share one address, from the XSRF-TOKEN cookie.
                // The plain handler is the one Spring documents for single-page apps.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )

                // Make sure the XSRF-TOKEN cookie is written on every response.
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)

                .cors(Customizer.withDefaults())

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Spring Boot forwards every error (400, 404, 500 ...) to /error.
                        // If /error is denied, a signed-in shopper sees a bare 403 (and a
                        // guest a 401) instead of the real problem.
                        .requestMatchers("/error").permitAll()

                        // ---- PUBLIC: read-only store APIs
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/store",
                                "/api/v1/categories",
                                "/api/v1/categories/**",
                                "/api/v1/products",
                                "/api/v1/products/**",
                                "/api/v1/images/**",
                                "/api/v1/auth/session",
                                "/api/v1/auth/csrf"
                        ).permitAll()

                        // ---- ADMIN
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // ---- CUSTOMER (signed in with Google)
                        .requestMatchers("/api/v1/me", "/api/v1/me/**").hasRole("CUSTOMER")

                        // ---- PUBLIC: guest and signed-in checkout, tracking, edit, cancel
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/orders",
                                "/api/v1/orders/lookup",
                                "/api/v1/orders/*/cancel"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/*").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/orders/*/items").permitAll()

                        // ---- Google OAuth
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                        // ---- WebSocket (every SUBSCRIBE is checked by StompAuthInterceptor)
                        .requestMatchers("/ws", "/ws/**").permitAll()

                        // ---- Health check
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                        .anyRequest().denyAll()
                )

                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo ->
                                userInfo.oidcUserService(oidcUserService)
                        )
                        .successHandler(successHandler(props))
                        .failureHandler(failureHandler(props))
                )

                .exceptionHandling(ex -> ex
                        // Not signed in -> 401
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // Signed in but not allowed, or CSRF check failed -> 403 with a JSON reason
                        .accessDeniedHandler(accessDeniedHandler())
                )

                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler(
                                new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)
                        )
                );

        return http.build();
    }

    /**
     * 403 answers in the same JSON shape as every other API error, and tell a CSRF
     * failure ("CSRF_INVALID", the browser can fetch a new token and retry) apart from a
     * real permission problem ("FORBIDDEN"). The reason is also logged so it shows up in
     * the Render logs.
     */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            boolean csrf = ex instanceof CsrfException;

            log.warn("403 {} {} reason={} origin={} signedIn={} csrfHeader={} csrfCookie={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    ex.getClass().getSimpleName(),
                    request.getHeader("Origin"),
                    request.getUserPrincipal() != null,
                    request.getHeader(CSRF_HEADER) != null,
                    hasCookie(request, CSRF_COOKIE));

            String code = csrf ? "CSRF_INVALID" : "FORBIDDEN";
            String message = csrf
                    ? "Your session expired or your browser blocked cookies. Please refresh the page and try again."
                    : "You don't have permission to do this.";

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"field\":null}");
        };
    }

    private static boolean hasCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * After Google sign-in:
     * Admin -> Admin orders.
     * Customer -> Store homepage.
     */
    private AuthenticationSuccessHandler successHandler(SgMartProperties props) {

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
    private AuthenticationFailureHandler failureHandler(SgMartProperties props) {

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
                    ? "no_access"
                    : "failed")
                    : "/?login_error="
                    + (SgMartOidcUserService.ERROR_EMAIL_NOT_VERIFIED.equals(code)
                    ? "email_not_verified"
                    : "failed");

            response.sendRedirect(props.publicUrl() + target);
        };
    }

    /**
     * CORS: which web app addresses may call the API from a browser.
     * Allowed = sgmart.cors-allowed-origins plus sgmart.public-url. A trailing "/" is
     * ignored, because the browser's Origin header never has one and a mismatch is
     * answered with 403 "Invalid CORS request".
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(SgMartProperties props) {

        Set<String> origins = new LinkedHashSet<>();
        if (props.corsAllowedOrigins() != null) {
            props.corsAllowedOrigins().forEach(o -> addOrigin(origins, o));
        }
        addOrigin(origins, props.publicUrl());

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(new ArrayList<>(origins));

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
                CSRF_HEADER,
                "X-Tracking-Token"
        ));

        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }

    private static void addOrigin(Set<String> target, String origin) {
        if (origin == null) {
            return;
        }
        String o = origin.trim();
        while (o.endsWith("/")) {
            o = o.substring(0, o.length() - 1);
        }
        if (!o.isEmpty()) {
            target.add(o);
        }
    }
}
