package com.sgmart.auth;

/** OWNER can do everything, including store settings and managing admins. MANAGER runs daily work. */
public enum AdminRole {
    OWNER,
    MANAGER
}
