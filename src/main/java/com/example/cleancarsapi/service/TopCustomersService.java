package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.OrgTimeZoneResolver;
import com.example.cleancarsapi.dto.TopCustomerResponse;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Overview-tab "highest revenue customers": lifetime (no date range), net of tax — service-order money received
 * plus AMCs sold to the cars/bikes the customer currently owns (an AMC follows its vehicle; AMC sales dated after
 * today aren't counted yet). Top {@value #LIMIT}, deleted customers excluded.
 */
@Service
@RequiredArgsConstructor
public class TopCustomersService {

    static final int LIMIT = 10;

    private final OrderRevenueReader orderRevenue;
    private final AmcSubscriptionRepository amcSubscriptions;
    private final CarRepository cars;
    private final BikeRepository bikes;
    private final CustomerRepository customers;
    private final OrgTimeZoneResolver orgTimezones;

    @Transactional(readOnly = true)
    public List<TopCustomerResponse> top(UUID orgId) {
        Map<UUID, Object[]> service = orderRevenue.lifetimeByCustomer(orgId);

        LocalDate today = orgTimezones.now().toLocalDate();
        List<AmcSubscription> sold = amcSubscriptions.findByOrgId(orgId).stream()
                .filter(s -> !s.getPaymentDate().isAfter(today))
                .toList();
        Map<UUID, UUID> carOwner = cars.findByOrgIdAndIdIn(orgId, ids(sold, AmcSubscription::getCarId)).stream()
                .collect(Collectors.toMap(Car::getId, Car::getCustomerId));
        Map<UUID, UUID> bikeOwner = bikes.findByOrgIdAndIdIn(orgId, ids(sold, AmcSubscription::getBikeId)).stream()
                .collect(Collectors.toMap(Bike::getId, Bike::getCustomerId));
        Map<UUID, BigDecimal> amcRevenue = new HashMap<>();
        Map<UUID, Long> amcCount = new HashMap<>();
        for (AmcSubscription s : sold) {
            UUID owner = s.getCarId() != null ? carOwner.get(s.getCarId()) : bikeOwner.get(s.getBikeId());
            if (owner != null) {
                amcRevenue.merge(owner, s.getSaleNet(), BigDecimal::add);
                amcCount.merge(owner, 1L, Long::sum);
            }
        }

        Set<UUID> customerIds = new HashSet<>(service.keySet());
        customerIds.addAll(amcRevenue.keySet());
        if (customerIds.isEmpty()) {
            return List.of();
        }
        return customers.findByOrgIdAndIdIn(orgId, customerIds).stream()
                .filter(c -> !c.isDeleted())
                .map(c -> {
                    Object[] agg = service.get(c.getId());
                    BigDecimal serviceRevenue = agg == null ? BigDecimal.ZERO : (BigDecimal) agg[0];
                    BigDecimal amc = amcRevenue.getOrDefault(c.getId(), BigDecimal.ZERO);
                    return new TopCustomerResponse(c.getId(), c.getName(), c.getPhone(),
                            agg == null ? 0L : (Long) agg[1], amcCount.getOrDefault(c.getId(), 0L),
                            serviceRevenue, amc, serviceRevenue.add(amc));
                })
                .filter(r -> r.totalRevenue().signum() > 0)
                .sorted(Comparator.comparing(TopCustomerResponse::totalRevenue).reversed()
                        .thenComparing(TopCustomerResponse::name))
                .limit(LIMIT)
                .toList();
    }

    private static List<UUID> ids(List<AmcSubscription> sales, Function<AmcSubscription, UUID> f) {
        List<UUID> out = sales.stream().map(f).filter(Objects::nonNull).distinct().toList();
        return out.isEmpty() ? List.of(new UUID(0, 0)) : out;
    }
}
