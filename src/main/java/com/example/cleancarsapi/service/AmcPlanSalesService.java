package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.OrgTimeZoneResolver;
import com.example.cleancarsapi.dto.AmcPlanSalesResponse;
import com.example.cleancarsapi.dto.AmcPlanSalesResponse.Counts;
import com.example.cleancarsapi.dto.AmcPlanSalesResponse.CurrentSale;
import com.example.cleancarsapi.dto.AmcPlanSalesResponse.VariantSales;
import com.example.cleancarsapi.dto.AmcSubscriptionResponse;
import com.example.cleancarsapi.entity.AmcPlan;
import com.example.cleancarsapi.entity.AmcPlanVariant;
import com.example.cleancarsapi.entity.AmcSubscription;
import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.AmcPlanRepository;
import com.example.cleancarsapi.repository.AmcPlanVariantRepository;
import com.example.cleancarsapi.repository.AmcSubscriptionRepository;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read-only sales picture of one AMC plan — totals, per-variant totals and the sales still in force. */
@Service
@RequiredArgsConstructor
public class AmcPlanSalesService {

    private final AmcPlanRepository plans;
    private final AmcPlanVariantRepository variants;
    private final AmcSubscriptionRepository subscriptions;
    private final CarRepository cars;
    private final BikeRepository bikes;
    private final CustomerRepository customers;
    private final AmcPlanGuard guard;
    private final AmcSubscriptionAssembler assembler;
    private final OrgTimeZoneResolver orgTimezones;

    @Transactional(readOnly = true)
    public AmcPlanSalesResponse get(UUID orgId, UUID planId) {
        guard.assertCanRead(orgId);
        AmcPlan plan = plans.findByIdAndOrgId(planId, orgId).orElseThrow(() -> new NotFoundException("amc plan", planId));
        List<AmcSubscription> sales = subscriptions.findByOrgIdAndPlanIdOrderByStartDateDescCreatedAtDesc(orgId, planId);
        LocalDate today = orgTimezones.now().toLocalDate();

        Map<UUID, AmcSlots.Status> status = new LinkedHashMap<>();
        for (AmcSubscription s : sales) {
            status.put(s.getId(), AmcSlots.compute(s.getStartDate(), s.getTenureMonths(), s.getIntervalMonths(), today, Set.of()).status());
        }

        List<VariantSales> perVariant = variants.findByPlanId(planId).stream()
                .sorted(Comparator.comparingInt(AmcPlanVariant::getTenureMonths).thenComparingInt(AmcPlanVariant::getIntervalMonths))
                .map(v -> new VariantSales(v.getId(), v.getTenureMonths(), v.getIntervalMonths(), v.isArchived(),
                        counts(sales.stream().filter(s -> s.getVariantId().equals(v.getId())).toList(), status)))
                .toList();

        List<AmcSubscription> current = sales.stream()
                .filter(s -> status.get(s.getId()) != AmcSlots.Status.EXPIRED)
                .sorted(Comparator.comparing((AmcSubscription s) -> status.get(s.getId()) == AmcSlots.Status.ACTIVE ? 0 : 1)
                        .thenComparing(AmcSubscription::getStartDate))
                .toList();
        return new AmcPlanSalesResponse(plan.getId(), plan.getName(), counts(sales, status), perVariant,
                currentSales(orgId, current));
    }

    private static Counts counts(List<AmcSubscription> sales, Map<UUID, AmcSlots.Status> status) {
        int active = 0;
        int upcoming = 0;
        int expired = 0;
        BigDecimal gross = BigDecimal.ZERO;
        for (AmcSubscription s : sales) {
            switch (status.get(s.getId())) {
                case ACTIVE -> active++;
                case UPCOMING -> upcoming++;
                case EXPIRED -> expired++;
            }
            gross = gross.add(s.getSaleGross());
        }
        return new Counts(sales.size(), active, upcoming, expired, gross);
    }

    private List<CurrentSale> currentSales(UUID orgId, List<AmcSubscription> current) {
        if (current.isEmpty()) {
            return List.of();
        }
        Map<UUID, Car> carById = cars.findByOrgIdAndIdIn(orgId, ids(current, AmcSubscription::getCarId)).stream()
                .collect(Collectors.toMap(Car::getId, c -> c));
        Map<UUID, Bike> bikeById = bikes.findByOrgIdAndIdIn(orgId, ids(current, AmcSubscription::getBikeId)).stream()
                .collect(Collectors.toMap(Bike::getId, b -> b));
        Set<UUID> ownerIds = new java.util.HashSet<>();
        carById.values().forEach(c -> ownerIds.add(c.getCustomerId()));
        bikeById.values().forEach(b -> ownerIds.add(b.getCustomerId()));
        Map<UUID, String> ownerNames = ownerIds.isEmpty() ? Map.of()
                : customers.findByOrgIdAndIdIn(orgId, ownerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, Customer::getName));

        List<AmcSubscriptionResponse> responses = assembler.toResponses(orgId, current);
        List<CurrentSale> out = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            AmcSubscription s = current.get(i);
            if (s.getCarId() != null) {
                Car car = carById.get(s.getCarId());
                out.add(new CurrentSale("CAR", car == null ? null : car.getCarNumber(),
                        car == null ? null : ownerNames.get(car.getCustomerId()), responses.get(i)));
            } else {
                Bike bike = bikeById.get(s.getBikeId());
                out.add(new CurrentSale("BIKE", bike == null ? null : bike.getBikeNumber(),
                        bike == null ? null : ownerNames.get(bike.getCustomerId()), responses.get(i)));
            }
        }
        return out;
    }

    private static List<UUID> ids(List<AmcSubscription> sales, java.util.function.Function<AmcSubscription, UUID> f) {
        List<UUID> out = sales.stream().map(f).filter(Objects::nonNull).distinct().toList();
        return out.isEmpty() ? List.of(new UUID(0, 0)) : out;
    }
}
