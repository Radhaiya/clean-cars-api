package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CarAndServicesResponse;
import com.example.cleancarsapi.dto.CarResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
/** READ half of the car CRUD — single fetch (with the car's service history) and paged listing. */
@Service
@RequiredArgsConstructor
public class CarReadService {

    private final CarRepository cars;
    private final CustomerRepository customers;
    private final ServiceOrderAssembler serviceOrders;
    private final AmcSubscriptionReadService amcSubscriptions;

    @Transactional(readOnly = true)
    public CarAndServicesResponse get(UUID orgId, UUID id) {
        Car car = cars.findByIdAndOrgIdAndDeletedFalse(id, orgId)
                .orElseThrow(() -> new NotFoundException("car", id));
        Customer owner = customers.findByIdAndOrgId(car.getCustomerId(), orgId).orElse(null);
        return CarAndServicesResponse.of(car, owner, serviceOrders.historyForCar(orgId, id),
                amcSubscriptions.listForVehicleOrEmpty(orgId, id, null));
    }

    @Transactional(readOnly = true)
    public PageResponse<CarResponse> list(UUID orgId, UUID customerId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        Page<Car> page = cars.search(orgId, customerId, term, searchKey(term), pageable);
        Set<UUID> deletedOwners = customers
                .findByOrgIdAndIdIn(orgId, page.getContent().stream().map(Car::getCustomerId).collect(Collectors.toSet()))
                .stream().filter(Customer::isDeleted).map(Customer::getId).collect(Collectors.toSet());
        return PageResponse.of(page.map(c -> CarResponse.from(c, deletedOwners.contains(c.getCustomerId()))));
    }

    private static String searchKey(String term) {
        String key = term == null ? "" : NormalizedKeys.vehicle(term);
        return key.isEmpty() ? null : key;
    }

    /** Whether another live car already has this number, ignoring spaces, hyphens and case. */
    @Transactional(readOnly = true)
    public boolean numberExists(UUID orgId, String number, UUID excludeId) {
        String key = NormalizedKeys.vehicle(number);
        return !key.isEmpty()
                && cars.existsByNumberKey(orgId, key, excludeId == null ? new UUID(0L, 0L) : excludeId);
    }
}
