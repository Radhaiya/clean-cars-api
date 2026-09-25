package com.example.cleancarsapi.controller.internal;

import com.example.cleancarsapi.dto.FirebaseLoginRequest;
import com.example.cleancarsapi.dto.LoginResponse;
import com.example.cleancarsapi.dto.LogoutRequest;
import com.example.cleancarsapi.dto.RefreshRequest;
import com.example.cleancarsapi.service.internal.InternalAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal console login — same shape as the tenant {@code /api/auth/**} routes
 * (Firebase-only), but gated by the email whitelist instead of user provisioning.
 * {@code /internal/api/auth/**} is permit-all; the whitelist is the gate itself.
 */
@RestController
@RequestMapping("/internal/api/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final InternalAuthService authService;

    /** Sign in with Firebase — whitelisted emails only, 403 email_not_whitelisted otherwise. */
    @PostMapping("/firebase")
    public LoginResponse firebase(@Valid @RequestBody FirebaseLoginRequest request) {
        return authService.loginWithFirebase(request.idToken());
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }
}
