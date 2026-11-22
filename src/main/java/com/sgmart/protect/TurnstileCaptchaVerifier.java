package com.sgmart.protect;

import com.sgmart.config.SgMartProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Cloudflare Turnstile (free). Set sgmart.captcha.turnstile-secret to turn it on.
 * With no secret (local development) every request passes and a warning is logged at start-up.
 */
@Component
public class TurnstileCaptchaVerifier implements CaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileCaptchaVerifier.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String secret;
    private final RestClient http;

    public TurnstileCaptchaVerifier(SgMartProperties props) {
        this.secret = props.turnstileSecret();
        this.http = RestClient.create();
        if (secret.isEmpty()) {
            log.warn("CAPTCHA is OFF (sgmart.captcha.turnstile-secret is empty). Turn it on before going live.");
        }
    }

    @Override
    public boolean isEnabled() {
        return !secret.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean verify(String token, String clientIp) {
        if (!isEnabled()) {
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);
        if (clientIp != null) {
            form.add("remoteip", clientIp);
        }
        try {
            Map<String, Object> result = http.post().uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return result != null && Boolean.TRUE.equals(result.get("success"));
        } catch (RestClientException e) {
            // If Cloudflare can't be reached, don't stop real customers from ordering.
            log.warn("Turnstile check failed to run, allowing the order: {}", e.getMessage());
            return true;
        }
    }
}
