package com.example.cleancarsapi.controller.internal;

import com.example.cleancarsapi.dto.internal.AllowedEmailRequest;
import com.example.cleancarsapi.dto.internal.AllowedEmailResponse;
import com.example.cleancarsapi.entity.internal.AllowedEmail;
import com.example.cleancarsapi.service.internal.WhitelistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Manage the console's login whitelist — the internal console's only writes. */
@RestController
@RequestMapping("/internal/api/allowed-emails")
@RequiredArgsConstructor
public class AllowedEmailController {

    private final WhitelistService whitelistService;

    @GetMapping
    public List<AllowedEmailResponse> list() {
        return whitelistService.list().stream().map(AllowedEmailResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AllowedEmailResponse add(@Valid @RequestBody AllowedEmailRequest request) {
        AllowedEmail row = whitelistService.add(request.email());
        return AllowedEmailResponse.from(row);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) {
        whitelistService.remove(id);
    }
}
