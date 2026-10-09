package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CustomerBikeSummary;
import com.example.cleancarsapi.dto.CustomerCarSummary;
import com.example.cleancarsapi.dto.CustomerResponse;
import com.example.cleancarsapi.dto.CustomerVehiclesResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.VehicleHistory;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.AmcSubscriptionRepository;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import com.example.cleancarsapi.service.internal.PlanLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
/** READ half of the customer CRUD — single fetch (with the customer's vehicles) and paged listing. */
@Service
@RequiredArgsConstructor
public class CustomerReadService {

    private final CustomerRepository customers;
    private final CarRepository cars;
    private final BikeRepository bikes;
    private final ServiceOrderAssembler serviceOrders;
    private final AmcSubscriptionRepository amcSubscriptions;
    private final PlanLimitService planLimits;

    @Transactional(readOnly = true)
    public CustomerVehiclesResponse get(UUID orgId, UUID id) {
        Customer customer = customers.findByIdAndOrgIdAndDeletedFalse(id, orgId)
                .orElseThrow(() -> new NotFoundException("customer", id));
        List<CustomerCarSummary> ownedCars = cars.findSummariesByCustomer(orgId, id);
        List<CustomerBikeSummary> ownedBikes = bikes.findSummariesByCustomer(orgId, id);
        List<VehicleHistory> histories = Stream.concat(
                ownedCars.stream().map(c -> serviceOrders.historyForCar(orgId, c.id())),
                ownedBikes.stream().map(b -> serviceOrders.historyForBike(orgId, b.id()))).toList();
        long totalServices = histories.stream().mapToLong(VehicleHistory::totalServices).sum();
        BigDecimal totalRevenue = histories.stream().map(VehicleHistory::totalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var plan = planLimits.currentPlan(orgId);
        BigDecimal amcRevenue = plan != null && plan.isAmcEnabled()
                ? amcSubscriptions.sumSaleGrossForVehicles(orgId, idsOrNone(ownedCars.stream().map(CustomerCarSummary::id)),
                        idsOrNone(ownedBikes.stream().map(CustomerBikeSummary::id)))
                : BigDecimal.ZERO;
        return CustomerVehiclesResponse.of(customer, ownedCars, ownedBikes, totalServices, totalRevenue, amcRevenue);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(UUID orgId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.of(customers.search(orgId, term, digitsKey(term), pageable).map(CustomerResponse::from));
    }

    private static String digitsKey(String term) {
        String digits = term == null ? "" : NormalizedKeys.phone(term);
        return digits.isEmpty() ? null : digits;
    }

    /** Whether another customer already has this phone, ignoring spaces, dashes, brackets and +. */
    @Transactional(readOnly = true)
    public boolean phoneExists(UUID orgId, String phone, UUID excludeId) {
        String digits = NormalizedKeys.phone(phone);
        return !digits.isEmpty()
                && customers.existsByPhoneDigits(orgId, digits, excludeId == null ? new UUID(0L, 0L) : excludeId);
    }

    /** An empty IN-list is invalid JPQL on some dialects — pass a never-matching id instead. */
    private static List<UUID> idsOrNone(Stream<UUID> ids) {
        List<UUID> list = ids.toList();
        return list.isEmpty() ? List.of(new UUID(0L, 0L)) : list;
    }
}
