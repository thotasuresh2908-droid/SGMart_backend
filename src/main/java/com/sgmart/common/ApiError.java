package com.sgmart.common;

/**
 * The one error shape every endpoint returns.
 * Example: { "code": "STOCK_NOT_ENOUGH", "message": "Only 3 Green Tea left", "field": "items[0].qty" }
 */
public record ApiError(String code, String message, String field) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }
}
