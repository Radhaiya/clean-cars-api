package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.BikeAndServicesResponse;
import com.example.cleancarsapi.dto.BikeResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
/** READ half of the bike CRUD — single fetch (with the bike's service history) and paged listing. */
@Service
@RequiredArgsConstructor
public class BikeReadService {

    private final BikeRepository bikes;
    private final CustomerRepository customers;
    private final ServiceOrderAssembler serviceOrders;
    private final AmcSubscriptionReadService amcSubscriptions;

    @Transactional(readOnly = true)
    public BikeAndServicesResponse get(UUID orgId, UUID id) {
        Bike bike = bikes.findByIdAndOrgIdAndDeletedFalse(id, orgId)
                .orElseThrow(() -> new NotFoundException("bike", id));
        Customer owner = customers.findByIdAndOrgId(bike.getCustomerId(), orgId).orElse(null);
        return BikeAndServicesResponse.of(bike, owner, serviceOrders.historyForBike(orgId, id),
                amcSubscriptions.listForVehicleOrEmpty(orgId, null, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<BikeResponse> list(UUID orgId, UUID customerId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        Page<Bike> page = bikes.search(orgId, customerId, term, searchKey(term), pageable);
        Set<UUID> deletedOwners = customers
                .findByOrgIdAndIdIn(orgId, page.getContent().stream().map(Bike::getCustomerId).collect(Collectors.toSet()))
                .stream().filter(Customer::isDeleted).map(Customer::getId).collect(Collectors.toSet());
        return PageResponse.of(page.map(b -> BikeResponse.from(b, deletedOwners.contains(b.getCustomerId()))));
    }

    private static String searchKey(String term) {
        String key = term == null ? "" : NormalizedKeys.vehicle(term);
        return key.isEmpty() ? null : key;
    }

    /** Whether another live bike already has this number, ignoring spaces, hyphens and case. */
    @Transactional(readOnly = true)
    public boolean numberExists(UUID orgId, String number, UUID excludeId) {
        String key = NormalizedKeys.vehicle(number);
        return !key.isEmpty()
                && bikes.existsByNumberKey(orgId, key, excludeId == null ? new UUID(0L, 0L) : excludeId);
    }
}
