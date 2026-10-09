package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.AmcSubscriptionResponse;
import com.example.cleancarsapi.entity.AmcSubscription;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.AmcSubscriptionRepository;
import com.example.cleancarsapi.service.internal.PlanLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** READ half of AMC sales — a vehicle's AMCs (active, upcoming and expired, newest first) with runtime counts. */
@Service
@RequiredArgsConstructor
public class AmcSubscriptionReadService {

    private final AmcSubscriptionRepository subscriptions;
    private final PlanLimitService planLimits;
    private final AmcSubscriptionAssembler assembler;

    @Transactional(readOnly = true)
    public List<AmcSubscriptionResponse> listForVehicle(UUID orgId, UUID carId, UUID bikeId) {
        planLimits.assertAmcEnabled(orgId);
        if ((carId == null) == (bikeId == null)) {
            throw new BadRequestException("Pass either carId or bikeId");
        }
        List<AmcSubscription> rows = carId != null
                ? subscriptions.findByOrgIdAndCarIdOrderByStartDateDescCreatedAtDesc(orgId, carId)
                : subscriptions.findByOrgIdAndBikeIdOrderByStartDateDescCreatedAtDesc(orgId, bikeId);
        return assembler.toResponses(orgId, rows);
    }

    /** A vehicle's AMCs embedded in its detail response — empty (not an error) when the plan has no AMC. */
    @Transactional(readOnly = true)
    public List<AmcSubscriptionResponse> listForVehicleOrEmpty(UUID orgId, UUID carId, UUID bikeId) {
        var plan = planLimits.currentPlan(orgId);
        if (plan == null || !plan.isAmcEnabled()) {
            return List.of();
        }
        return listForVehicle(orgId, carId, bikeId);
    }

    @Transactional(readOnly = true)
    public AmcSubscriptionResponse get(UUID orgId, UUID id) {
        planLimits.assertAmcEnabled(orgId);
        AmcSubscription row = subscriptions.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("amc", id));
        return assembler.toResponse(orgId, row);
    }
}
