package com.sgmart.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.util.Collection;

/**
 * The signed-in person, stored in the session. Wraps the Google user and adds
 * our own id: admin_users.id for admins, customers.id for shoppers.
 */
public class SgMartPrincipal extends DefaultOidcUser {

    public enum Kind { ADMIN, CUSTOMER }

    private final Kind kind;
    private final Long appUserId;
    private final String displayName;
    private final AdminRole adminRole;

    public SgMartPrincipal(Kind kind, Long appUserId, String displayName, AdminRole adminRole,
                           Collection<? extends GrantedAuthority> authorities,
                           OidcIdToken idToken, OidcUserInfo userInfo) {
        super(authorities, idToken, userInfo, "sub");
        this.kind = kind;
        this.appUserId = appUserId;
        this.displayName = displayName;
        this.adminRole = adminRole;
    }

    public Kind getKind() { return kind; }
    public Long getAppUserId() { return appUserId; }
    public String getDisplayName() { return displayName; }
    public AdminRole getAdminRole() { return adminRole; }
    public boolean isAdmin() { return kind == Kind.ADMIN; }
}
