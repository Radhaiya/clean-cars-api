package com.example.cleancarsapi.exception;

/** The request is malformed in a way bean validation can't express (HTTP 400). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
