package com.sgmart.media;

/** The uploaded file can't be used as a product photo. The message is shown to the admin. */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }
}
