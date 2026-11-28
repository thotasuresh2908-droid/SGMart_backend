
package com.sgmart.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ensures that the CSRF token is generated and exposed
 * through the XSRF-TOKEN cookie for the React frontend.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        CsrfToken csrfToken = (CsrfToken) request.getAttribute(
                CsrfToken.class.getName()
        );

        // Force lazy CSRF token generation.
        if (csrfToken != null) {
            csrfToken.getToken();
        }

        chain.doFilter(request, response);
    }
}
