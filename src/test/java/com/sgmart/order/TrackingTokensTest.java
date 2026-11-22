package com.sgmart.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingTokensTest {

    @Test
    void tokensAreLongRandomAndUrlSafe() {
        String a = TrackingTokens.newToken();
        String b = TrackingTokens.newToken();
        assertThat(a).hasSize(32).matches("[A-Za-z0-9_-]+");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void onlyTheRightTokenMatchesItsHash() {
        String token = TrackingTokens.newToken();
        String hash = TrackingTokens.hash(token);
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
        assertThat(TrackingTokens.matches(token, hash)).isTrue();
        assertThat(TrackingTokens.matches(" " + token + " ", hash)).isTrue();
        assertThat(TrackingTokens.matches(TrackingTokens.newToken(), hash)).isFalse();
        assertThat(TrackingTokens.matches(null, hash)).isFalse();
        assertThat(TrackingTokens.matches("", hash)).isFalse();
    }
}
