package com.sgmart.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets the React app ask "who is signed in?" on start-up. */
@RestController
public class AuthController {

    public record SessionInfo(String type, Long id, String name, String email, AdminRole role) {
        static SessionInfo guest() {
            return new SessionInfo("GUEST", null, null, null, null);
        }
    }

    @GetMapping("/api/v1/auth/session")
    public SessionInfo session(@AuthenticationPrincipal SgMartPrincipal principal) {
        if (principal == null) {
            return SessionInfo.guest();
        }
        return new SessionInfo(principal.getKind().name(), principal.getAppUserId(),
                principal.getDisplayName(), principal.getEmail(), principal.getAdminRole());
    }
}
