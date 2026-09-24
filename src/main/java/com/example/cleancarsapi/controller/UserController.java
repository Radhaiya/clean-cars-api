package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.UserProfile;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    /**
     * Voluntary exit from the org ({@link UserService#leaveOrg} — owner cannot).
     * Typically called right after an owner-issued token got them back in.
     */
    @PostMapping("/org/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveOrg() {
        userService.leaveOrg();
    }
}
