package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.OrgTimeZoneResolver;
import com.example.cleancarsapi.dto.AmcSaleRequest;
import com.example.cleancarsapi.dto.AmcSubscriptionResponse;
import com.example.cleancarsapi.dto.AmcVariantRequest;
import com.example.cleancarsapi.dto.TaxBreakdown;
import com.example.cleancarsapi.entity.AmcPlan;
import com.example.cleancarsapi.entity.AmcPlanItem;
import com.example.cleancarsapi.entity.AmcPlanVariant;
import com.example.cleancarsapi.entity.AmcSubscription;
import com.example.cleancarsapi.entity.AmcSubscriptionItem;
import com.example.cleancarsapi.entity.AmcVariantRow;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.AmcPlanItemRepository;
import com.example.cleancarsapi.repository.AmcPlanRepository;
import com.example.cleancarsapi.repository.AmcPlanVariantRepository;
import com.example.cleancarsapi.repository.AmcSubscriptionItemRepository;
import com.example.cleancarsapi.repository.AmcSubscriptionRepository;
import com.example.cleancarsapi.repository.AmcVariantRowRepository;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.EmployeeRepository;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.internal.PlanLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CREATE half of AMC sales (docs/FEATURE-AMC.md). A sale is final — there is no update, cancel or refund.
 * Snapshots the variant (or the caller's row overrides), prices the whole tenure (Σ rows × slots, tax per
 * row) and records the single upfront payment. Anyone in the org can sell, as long as the plan includes AMC.
 */
@Service
@RequiredArgsConstructor
public class AmcSubscriptionCreateService {

    /** A sale may be scheduled to start at most this far ahead. */
    private static final int MAX_START_AHEAD_DAYS = 366;

    private final AmcSubscriptionRepository subscriptions;
    private final AmcSubscriptionItemRepository subscriptionItems;
    private final AmcPlanRepository plans;
    private final AmcPlanItemRepository planItems;
    private final AmcPlanVariantRepository variants;
    private final AmcVariantRowRepository variantRows;
    private final AmcVariantRowFactory rowFactory;
    private final CarRepository cars;
    private final BikeRepository bikes;
    private final EmployeeRepository employees;
    private final PlanLimitService planLimits;
    private final OrgTimeZoneResolver orgTimezones;
    private final AmcSubscriptionAssembler assembler;

    @Transactional
    public AmcSubscriptionResponse create(UUID orgId, AmcSaleRequest request) {
        planLimits.assertAmcEnabled(orgId);
        requireVehicle(orgId, request);

        AmcPlanVariant variant = variants.findByIdAndOrgId(request.variantId(), orgId)
                .orElseThrow(() -> new NotFoundException("amc variant", request.variantId()));
        AmcPlan plan = plans.findByIdAndOrgId(variant.getPlanId(), orgId)
                .orElseThrow(() -> new NotFoundException("amc plan", variant.getPlanId()));
        if (plan.isArchived() || variant.isArchived()) {
            throw ConflictException.amcVariantArchived();
        }
        if (request.sellerEmployeeId() != null && !employees.existsByIdAndOrgId(request.sellerEmployeeId(), orgId)) {
            throw new NotFoundException("employee", request.sellerEmployeeId());
        }

        LocalDate today = orgTimezones.now().toLocalDate();
        LocalDate start = request.startDate() != null ? request.startDate() : today;
        if (start.isBefore(today)) {
            throw new BadRequestException("The start date cannot be in the past");
        }
        if (start.isAfter(today.plusDays(MAX_START_AHEAD_DAYS))) {
            throw new BadRequestException("The start date is too far ahead");
        }

        // The rows the customer is sold: the caller's overrides (validated against the plan) or the variant's own.
        List<AmcPlanItem> items = planItems.findByPlanIdOrderByPositionAsc(plan.getId());
        List<AmcVariantRow> rows = request.rows() != null && !request.rows().isEmpty()
                ? rowFactory.build(null, items, request.rows())
                : variantRows.findByVariantIdIn(List.of(variant.getId())).stream()
                        .sorted(java.util.Comparator.comparingInt(r -> position(items, r)))
                        .toList();

        TaxBreakdown bundle = rows.stream()
                .map(r -> TaxBreakdown.of(r.getPrice(), r.getTaxPercentage(), r.isTaxIncluded()).times(r.getQuantity()))
                .reduce(TaxBreakdown.zero(), TaxBreakdown::plus);
        TaxBreakdown total = bundle.times(variant.totalSlots());

        AmcSubscription sale = new AmcSubscription();
        sale.setOrgId(orgId);
        sale.setCarId(request.carId());
        sale.setBikeId(request.bikeId());
        sale.setPlanId(plan.getId());
        sale.setVariantId(variant.getId());
        sale.setPlanName(plan.getName());
        sale.setTenureMonths(variant.getTenureMonths());
        sale.setIntervalMonths(variant.getIntervalMonths());
        sale.setStartDate(start);
        sale.setSaleNet(total.net());
        sale.setSaleTax(total.tax());
        sale.setSaleGross(total.gross());
        sale.setPaymentType(request.paymentType());
        sale.setPaymentDate(request.paymentDate() != null ? request.paymentDate() : today);
        sale.setReceivedBy(AuthContext.require().userId());
        sale.setSoldByEmployeeId(request.sellerEmployeeId());
        AmcSubscription saved = subscriptions.save(sale);

        List<AmcSubscriptionItem> snapshot = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            AmcVariantRow r = rows.get(i);
            AmcSubscriptionItem item = new AmcSubscriptionItem();
            item.setSubscriptionId(saved.getId());
            item.setServiceName(nameOf(items, r));
            item.setPosition(i);
            item.setQuantity(r.getQuantity());
            item.setPrice(r.getPrice());
            item.setTaxPercentage(r.getTaxPercentage());
            item.setTaxIncluded(r.isTaxIncluded());
            snapshot.add(item);
        }
        subscriptionItems.saveAll(snapshot);
        return assembler.toResponse(orgId, saved);
    }

    private void requireVehicle(UUID orgId, AmcSaleRequest request) {
        if ((request.carId() == null) == (request.bikeId() == null)) {
            throw new BadRequestException("An AMC belongs to one vehicle — set either carId or bikeId");
        }
        if (request.carId() != null) {
            cars.findByIdAndOrgIdAndDeletedFalse(request.carId(), orgId)
                    .orElseThrow(() -> new NotFoundException("car", request.carId()));
        } else {
            bikes.findByIdAndOrgIdAndDeletedFalse(request.bikeId(), orgId)
                    .orElseThrow(() -> new NotFoundException("bike", request.bikeId()));
        }
    }

    private static int position(List<AmcPlanItem> items, AmcVariantRow row) {
        return items.stream().filter(i -> i.getId().equals(row.getPlanItemId())).findFirst()
                .map(AmcPlanItem::getPosition).orElse(0);
    }

    private static String nameOf(List<AmcPlanItem> items, AmcVariantRow row) {
        return items.stream().filter(i -> i.getId().equals(row.getPlanItemId())).findFirst()
                .map(AmcPlanItem::getServiceName).orElseThrow();
    }
}
