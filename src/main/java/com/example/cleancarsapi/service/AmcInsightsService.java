package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.OrgTimeZoneResolver;
import com.example.cleancarsapi.dto.AmcExpiringResponse;
import com.example.cleancarsapi.dto.AmcSubscriptionResponse;
import com.example.cleancarsapi.dto.AmcUsageResponse;
import com.example.cleancarsapi.dto.AmcUsageResponse.PlanUsage;
import com.example.cleancarsapi.entity.AmcSubscription;
import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.repository.AmcSubscriptionRepository;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Statistics-page AMC snapshots that have no date range — they describe the AMCs as of today:
 * slot usage across every sold AMC, and the active AMCs about to end (renewal list).
 */
@Service
@RequiredArgsConstructor
public class AmcInsightsService {

    /** The renewal window: AMCs ending today through this many days ahead. */
    static final int EXPIRING_WINDOW_DAYS = 30;

    private final AmcSubscriptionRepository subscriptions;
    private final AmcSubscriptionAssembler assembler;
    private final AmcPlanGuard guard;
    private final CarRepository cars;
    private final BikeRepository bikes;
    private final CustomerRepository customers;
    private final OrgTimeZoneResolver orgTimezones;

    @Transactional(readOnly = true)
    public AmcUsageResponse usage(UUID orgId) {
        guard.assertCanRead(orgId);
        List<AmcSubscription> all = subscriptions.findByOrgId(orgId);
        Map<String, int[]> byPlan = new LinkedHashMap<>(); // planName -> {used, remaining, lapsed}
        int used = 0;
        int remaining = 0;
        int lapsed = 0;
        for (AmcSubscriptionResponse r : assembler.toResponses(orgId, all)) {
            used += r.used();
            remaining += r.remaining();
            lapsed += r.lapsed();
            int[] p = byPlan.computeIfAbsent(r.planName(), k -> new int[3]);
            p[0] += r.used();
            p[1] += r.remaining();
            p[2] += r.lapsed();
        }
        List<PlanUsage> plans = byPlan.entrySet().stream()
                .map(e -> new PlanUsage(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .sorted(Comparator.comparingInt((PlanUsage p) -> p.used() + p.remaining() + p.lapsed()).reversed()
                        .thenComparing(PlanUsage::planName))
                .toList();
        return new AmcUsageResponse(used, remaining, lapsed, plans);
    }

    /** ACTIVE AMCs whose end date falls within the next {@value #EXPIRING_WINDOW_DAYS} days, soonest first. */
    @Transactional(readOnly = true)
    public List<AmcExpiringResponse> expiringSoon(UUID orgId) {
        guard.assertCanRead(orgId);
        LocalDate today = orgTimezones.now().toLocalDate();
        LocalDate until = today.plusDays(EXPIRING_WINDOW_DAYS);
        List<AmcSubscriptionResponse> ending = assembler.toResponses(orgId, subscriptions.findByOrgId(orgId)).stream()
                .filter(r -> r.status() == AmcSlots.Status.ACTIVE
                        && !r.endDate().isBefore(today) && !r.endDate().isAfter(until))
                .sorted(Comparator.comparing(AmcSubscriptionResponse::endDate))
                .toList();
        if (ending.isEmpty()) {
            return List.of();
        }
        Map<UUID, Car> carById = cars.findByOrgIdAndIdIn(orgId, ids(ending, AmcSubscriptionResponse::carId)).stream()
                .collect(Collectors.toMap(Car::getId, c -> c));
        Map<UUID, Bike> bikeById = bikes.findByOrgIdAndIdIn(orgId, ids(ending, AmcSubscriptionResponse::bikeId)).stream()
                .collect(Collectors.toMap(Bike::getId, b -> b));
        Set<UUID> ownerIds = new HashSet<>();
        carById.values().forEach(c -> ownerIds.add(c.getCustomerId()));
        bikeById.values().forEach(b -> ownerIds.add(b.getCustomerId()));
        Map<UUID, Customer> owners = ownerIds.isEmpty() ? Map.of()
                : customers.findByOrgIdAndIdIn(orgId, ownerIds).stream().collect(Collectors.toMap(Customer::getId, c -> c));

        return ending.stream().map(r -> {
            boolean isCar = r.carId() != null;
            Car car = isCar ? carById.get(r.carId()) : null;
            Bike bike = isCar ? null : bikeById.get(r.bikeId());
            UUID ownerId = car != null ? car.getCustomerId() : bike != null ? bike.getCustomerId() : null;
            Customer owner = ownerId == null ? null : owners.get(ownerId);
            return new AmcExpiringResponse(r.id(), isCar ? "CAR" : "BIKE",
                    car != null ? car.getCarNumber() : bike != null ? bike.getBikeNumber() : null,
                    owner == null ? null : owner.getName(), owner == null ? null : owner.getPhone(),
                    r.planName(), r.endDate(), ChronoUnit.DAYS.between(today, r.endDate()), r.remaining());
        }).toList();
    }

    private static List<UUID> ids(List<AmcSubscriptionResponse> rows, Function<AmcSubscriptionResponse, UUID> f) {
        List<UUID> out = rows.stream().map(f).filter(Objects::nonNull).distinct().toList();
        return out.isEmpty() ? List.of(new UUID(0, 0)) : out;
    }
}
