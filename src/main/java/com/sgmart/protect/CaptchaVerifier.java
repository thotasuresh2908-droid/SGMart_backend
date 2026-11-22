package com.sgmart.protect;

/** "I'm not a robot" check on checkout. */
public interface CaptchaVerifier {

    /** True when the token from the browser is valid (or when the check is turned off). */
    boolean verify(String token, String clientIp);

    boolean isEnabled();
}
