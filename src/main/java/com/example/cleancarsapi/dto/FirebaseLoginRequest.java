package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param idToken the Firebase Auth ID token obtained by the client after signing in
 *                via any provider (Google, Apple, phone OTP)
 */
public record FirebaseLoginRequest(@NotBlank String idToken) {
}
