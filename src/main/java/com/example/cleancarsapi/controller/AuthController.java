package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.FirebaseLoginRequest;
import com.example.cleancarsapi.dto.LoginResponse;
import com.example.cleancarsapi.dto.LogoutRequest;
import com.example.cleancarsapi.dto.RefreshRequest;
import com.example.cleancarsapi.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Sign in with Firebase (Google / Apple / phone OTP) — the only login path, no password. */
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
