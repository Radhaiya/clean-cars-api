package com.example.cleancarsapi.exception;

/** Thrown when the caller is authenticated but not allowed to perform the action. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
