package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.UserProfile;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Current user, resolved from the Bearer token via {@link AuthContext}. */
    @GetMapping("/me")
    public UserProfile me() {
        return userService.getProfile(AuthContext.require().userId());
    }
}
