package com.sgmart.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets the React app ask "who is signed in?" and get a CSRF token on start-up. */
@RestController
public class AuthController {

    public record SessionInfo(String type, Long id, String name, String email, AdminRole role) {
        static SessionInfo guest() {
            return new SessionInfo("GUEST", null, null, null, null);
        }
    }

    /** The token the browser must send back in the header named here on every POST, PUT and DELETE. */
    public record CsrfInfo(String headerName, String token) {
    }

    @GetMapping("/api/v1/auth/session")
    public SessionInfo session(@AuthenticationPrincipal SgMartPrincipal principal) {
        if (principal == null) {
            return SessionInfo.guest();
        }
        return new SessionInfo(principal.getKind().name(), principal.getAppUserId(),
                principal.getDisplayName(), principal.getEmail(), principal.getAdminRole());
    }

    /**
     * The XSRF-TOKEN cookie belongs to the API's address, so when the web app runs on a
     * different address (Vercel -> Render) its JavaScript cannot read that cookie. This
     * endpoint hands the same token over in the response body instead. The cookie is set on
     * this response too, so the browser sends it back with the header: Spring checks they match.
     */
    @GetMapping("/api/v1/auth/csrf")
    public CsrfInfo csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            return new CsrfInfo("X-XSRF-TOKEN", null);
        }
        return new CsrfInfo(token.getHeaderName(), token.getToken());
    }
}
