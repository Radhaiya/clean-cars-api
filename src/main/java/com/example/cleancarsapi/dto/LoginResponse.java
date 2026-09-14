package com.example.cleancarsapi.dto;

public record LoginResponse(String token, String tokenType, long expiresIn, String refreshToken) {

    public static LoginResponse bearer(String token, long expiresIn, String refreshToken) {
        return new LoginResponse(token, "Bearer", expiresIn, refreshToken);
    }
}
