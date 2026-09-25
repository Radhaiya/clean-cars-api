package com.example.cleancarsapi.controller.internal;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.internal.InternalSubscriptionResponse;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.service.SubscriptionSyncService;
import com.example.cleancarsapi.service.internal.SubscriptionReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Append-only subscription history for the console — terminal rows included. */
@RestController
@RequestMapping("/internal/api/subscriptions")
@RequiredArgsConstructor
public class InternalSubscriptionController {

    private final SubscriptionReadService subscriptionReadService;
    private final SubscriptionSyncService subscriptionSyncService;

    @GetMapping
    public PageResponse<InternalSubscriptionResponse> list(
            @RequestParam(required = false) java.util.UUID orgId,
            @RequestParam(required = false) SubscriptionStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(subscriptionReadService.searchAcrossOrgs(orgId, status, pageable));
    }

    /**
     * Pull-fresh: reconcile a row against Razorpay's own state (webhook-loss
     * recovery, support runbook for stuck checkouts whose UI stopped polling).
     * Razorpay being down throws (fail-loud — admin sees it, unlike the
     * fail-soft customer polling path). Already-synced / not-applicable rows
     * come back unchanged; the refresh alone tells support what moved.
     */
    @PostMapping("/{subscriptionId}/sync")
    public InternalSubscriptionResponse sync(@PathVariable UUID subscriptionId) {
        subscriptionSyncService.syncFromRazorpay(subscriptionId, SubscriptionSyncService.Mode.FAIL_LOUD);
        return subscriptionReadService.rowForConsole(subscriptionId);
    }
}
