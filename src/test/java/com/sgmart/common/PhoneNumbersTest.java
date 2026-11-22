package com.sgmart.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"9848012345", "98480 12345", "+91 98480-12345", "919848012345", "09848012345"})
    void acceptsWhatPeopleType(String typed) {
        assertThat(PhoneNumbers.normalize(typed, "mobile")).isEqualTo("9848012345");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567890", "5848012345", "98480123456", ""})
    void rejectsInvalidNumbers(String typed) {
        assertThatThrownBy(() -> PhoneNumbers.normalize(typed, "mobile"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("INVALID_MOBILE");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> PhoneNumbers.normalize(null, "mobile")).isInstanceOf(ApiException.class);
    }
}
