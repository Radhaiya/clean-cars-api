package com.example.cleancarsapi.controller.internal;

import com.example.cleancarsapi.dto.internal.SubscriptionPlanRequest;
import com.example.cleancarsapi.dto.internal.SubscriptionPlanResponse;
import com.example.cleancarsapi.entity.SubscriptionPlan;
import com.example.cleancarsapi.security.internal.InternalAuthContext;
import com.example.cleancarsapi.service.internal.SubscriptionPlanAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** CRUD over the subscription-plan catalogue — the console's plan editor. */
@RestController
@RequestMapping("/internal/api/plans")
@RequiredArgsConstructor
public class InternalPlanController {

    private final SubscriptionPlanAdminService planService;

    @GetMapping
    public List<SubscriptionPlanResponse> list() {
        return planService.list().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public SubscriptionPlanResponse get(@PathVariable UUID id) {
        return toResponse(planService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionPlanResponse create(@Valid @RequestBody SubscriptionPlanRequest request) {
        return toResponse(planService.create(request, InternalAuthContext.require().email()));
    }

    /** Full replace of the editable fields. */
    @PutMapping("/{id}")
    public SubscriptionPlanResponse update(@PathVariable UUID id,
                                           @Valid @RequestBody SubscriptionPlanRequest request) {
        return toResponse(planService.update(id, request, InternalAuthContext.require().email()));
    }

    /** 409 for the Trial plan or a plan that has subscriptions — hide it instead. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        planService.delete(id, InternalAuthContext.require().email());
    }

    private SubscriptionPlanResponse toResponse(SubscriptionPlan plan) {
        return SubscriptionPlanResponse.from(plan, planService.subscriptionCount(plan.getId()));
    }
}
