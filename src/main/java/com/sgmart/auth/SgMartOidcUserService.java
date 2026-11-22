package com.sgmart.auth;

import com.sgmart.customer.Customer;
import com.sgmart.customer.CustomerService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Runs after Google confirms who the person is.
 * "google-admin"    -> must be an active email in admin_users, else access is refused.
 * "google-customer" -> finds or creates the shopper.
 */
@Service
public class SgMartOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    public static final String ADMIN_REGISTRATION = "google-admin";
    public static final String CUSTOMER_REGISTRATION = "google-customer";
    public static final String ERROR_NO_ADMIN_ACCESS = "no_admin_access";
    public static final String ERROR_EMAIL_NOT_VERIFIED = "email_not_verified";

    private final OidcUserService googleUsers = new OidcUserService();
    private final AdminUserService adminUsers;
    private final CustomerService customers;

    public SgMartOidcUserService(AdminUserService adminUsers, CustomerService customers) {
        this.adminUsers = adminUsers;
        this.customers = customers;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser google = googleUsers.loadUser(request);
        String email = google.getEmail();
        if (email == null || !Boolean.TRUE.equals(google.getEmailVerified())) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ERROR_EMAIL_NOT_VERIFIED),
                    "Your Google account email is not verified.");
        }

        String registration = request.getClientRegistration().getRegistrationId();
        if (ADMIN_REGISTRATION.equals(registration)) {
            AdminUser admin = adminUsers.recordSignIn(email).orElseThrow(() ->
                    new OAuth2AuthenticationException(new OAuth2Error(ERROR_NO_ADMIN_ACCESS),
                            "This Google account doesn't have admin access."));
            List<GrantedAuthority> roles = List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_" + admin.getRole().name()));
            return new SgMartPrincipal(SgMartPrincipal.Kind.ADMIN, admin.getId(), admin.getName(),
                    admin.getRole(), roles, google.getIdToken(), google.getUserInfo());
        }

        if (CUSTOMER_REGISTRATION.equals(registration)) {
            String name = google.getFullName() != null ? google.getFullName() : email.substring(0, email.indexOf('@'));
            Customer customer = customers.findOrCreateFromGoogle(google.getSubject(), email, name);
            return new SgMartPrincipal(SgMartPrincipal.Kind.CUSTOMER, customer.getId(), customer.getName(),
                    null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")),
                    google.getIdToken(), google.getUserInfo());
        }

        throw new OAuth2AuthenticationException(new OAuth2Error("unknown_client"), "Unknown sign-in option.");
    }
}
