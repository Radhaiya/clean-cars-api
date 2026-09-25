package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.ChangePlanRequest;
import com.example.cleancarsapi.dto.ChangePlanResponse;
import com.example.cleancarsapi.dto.CurrentSubscriptionResponse;
import com.example.cleancarsapi.dto.StartTrialRequest;
import com.example.cleancarsapi.dto.StartTrialResponse;
import com.example.cleancarsapi.dto.SubscribeRequest;
import com.example.cleancarsapi.dto.SubscribeResponse;
import com.example.cleancarsapi.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
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

    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscribeResponse subscribe(@Valid @RequestBody SubscribeRequest request) {
        return subscriptionService.subscribe(request);
    }

    /**
     * Release a stuck {@code PENDING} checkout (abandoned / Razorpay call failed) so
     * the org can subscribe again. Owner-only; no pending row → 409
     * {@code no_pending_checkout}. The UI polls {@code GET /api/subscription/{id}}
     * after checkout; if it stays {@code PENDING} too long, calling this unblocks.
     */
    @PostMapping("/cancel-checkout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelCheckout() {
        subscriptionService.cancelPendingCheckoutForCaller();
    }

    @PostMapping("/change-plan")
    public ChangePlanResponse changePlan(@Valid @RequestBody ChangePlanRequest request) {
        return subscriptionService.changePlan(request);
    }

    /**
     * Checkout polling callback (UI calls it in a loop after Checkout opens, keyed
     * by the {@code subscriptionId} returned by {@code /subscribe}). Same body shape
     * as {@code GET /api/subscription}; the UI redirects when {@code active=true}.
     * 404 for an id that is not this org's row.
     */
    @GetMapping("/{subscriptionId}")
    public CurrentSubscriptionResponse status(@PathVariable UUID subscriptionId) {
        return subscriptionService.getStatus(subscriptionId);
    }
}
