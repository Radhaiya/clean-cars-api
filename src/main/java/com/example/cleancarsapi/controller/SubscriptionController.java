package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.CurrentSubscriptionResponse;
import com.example.cleancarsapi.dto.StartTrialRequest;
import com.example.cleancarsapi.dto.StartTrialResponse;
import com.example.cleancarsapi.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The caller's subscription: view the current plan, start a free trial. */
@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /** Current plan — trial or paid. Always 200; {@code active:false} / {@code status:"NONE"} when there is none (incl. org-less callers). */
    @GetMapping
    public CurrentSubscriptionResponse current() {
        return subscriptionService.getCurrent();
    }

    /**
     * Start a free trial. For an org-less user this also creates their organization
     * (org details in the body) and returns a fresh access token scoped to it.
     */
    @PostMapping("/trial")
    @ResponseStatus(HttpStatus.CREATED)
    public StartTrialResponse startTrial(@Valid @RequestBody StartTrialRequest request) {
        return subscriptionService.startTrial(request);
    }
}
