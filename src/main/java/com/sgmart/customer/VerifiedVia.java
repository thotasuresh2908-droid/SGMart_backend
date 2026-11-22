package com.sgmart.customer;

/** How the customer's mobile number was confirmed. NONE shows the "New customer" badge to admins. */
public enum VerifiedVia {
    NONE,
    STORE_CALL,
    GOOGLE
}
