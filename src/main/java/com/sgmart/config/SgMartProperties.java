package com.sgmart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * App settings under "sgmart.*" in application.yml.
 *
 * @param publicUrl          where people open the web app, e.g. http://localhost:5173 or https://sgmart.in;
 *                           used for Google redirect URIs and where to send people after sign-in
 * @param corsAllowedOrigins web app origins allowed to call the API from a browser
 * @param images             where product photos are kept
 * @param captcha            Cloudflare Turnstile check on checkout (off when no secret is set)
 * @param orders             limits that stop fake orders
 */
@ConfigurationProperties(prefix = "sgmart")
public record SgMartProperties(String publicUrl, List<String> corsAllowedOrigins, Images images,
                               Captcha captcha, Orders orders) {

    /** @param dir folder for product photos (mount it as a volume in production) */
    public record Images(String dir) {
    }

    /** @param turnstileSecret Cloudflare Turnstile secret key; empty turns the check off (local development) */
    public record Captcha(String turnstileSecret) {
    }

    /** @param maxPerIpPer10Minutes how many orders one IP address can place in 10 minutes */
    public record Orders(Integer maxPerIpPer10Minutes) {
    }

    public String imagesDir() {
        return images != null && images.dir() != null && !images.dir().isBlank() ? images.dir() : "data/images";
    }

    public String turnstileSecret() {
        return captcha != null && captcha.turnstileSecret() != null ? captcha.turnstileSecret().trim() : "";
    }

    public int maxOrdersPerIpPer10Minutes() {
        return orders != null && orders.maxPerIpPer10Minutes() != null ? orders.maxPerIpPer10Minutes() : 5;
    }
}
