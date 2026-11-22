package com.sgmart.common;

import java.util.regex.Pattern;

/** Indian mobile numbers: 10 digits starting with 6, 7, 8 or 9 (same rule as the database). */
public final class PhoneNumbers {

    private static final Pattern VALID = Pattern.compile("^[6-9][0-9]{9}$");

    private PhoneNumbers() {
    }

    /**
     * Accepts what people type ("98480 12345", "+91 98480-12345", "098480 12345")
     * and returns the 10-digit form, or throws INVALID_MOBILE.
     */
    public static String normalize(String input, String field) {
        String digits = input == null ? "" : input.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!VALID.matcher(digits).matches()) {
            throw ApiException.badRequest("INVALID_MOBILE", "Enter a valid 10-digit mobile number.", field);
        }
        return digits;
    }
}
