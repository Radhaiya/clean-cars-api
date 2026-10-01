package com.example.cleancarsapi.exception;

import lombok.Getter;

/** The service is deliberately offline (HTTP 503), e.g. maintenance mode. */
@Getter
public class ServiceUnavailableException extends RuntimeException {

    /** Machine-readable code for the UI to branch on, e.g. {@code maintenance_mode}. */
    private final String code;

    public ServiceUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static ServiceUnavailableException maintenanceMode() {
        return new ServiceUnavailableException("maintenance_mode",
                "My Garage One is down for maintenance — please try again shortly");
    }
}
