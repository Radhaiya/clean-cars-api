package com.example.cleancarsapi.exception;

import lombok.Getter;

/** Thrown when the caller is authenticated but not allowed to perform the action. */
@Getter
public class ForbiddenException extends RuntimeException {

    /** Optional machine-readable code for the UI to branch on, e.g. {@code new_logins_disabled}. */
    private final String code;

    public ForbiddenException(String message) {
        this(null, message);
    }

    public ForbiddenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static ForbiddenException newLoginsDisabled() {
        return new ForbiddenException("new_logins_disabled",
                "Login for new users is currently disabled");
    }
}
