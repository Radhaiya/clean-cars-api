package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.dto.ServiceOrderSummaryResponse;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import com.example.cleancarsapi.entity.Vendor;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import com.example.cleancarsapi.repository.EmployeeRepository;
import com.example.cleancarsapi.repository.ServiceOrderItemRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import com.example.cleancarsapi.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
/** READ half of the service-order CRUD — full single fetch, lightweight paged list. */
@Service
@RequiredArgsConstructor
public class ServiceOrderReadService {

    private final ServiceOrderRepository orders;
    private final ServiceOrderItemRepository items;
    private final CarRepository cars;
    private final CustomerRepository customers;
    private final EmployeeRepository employees;
    private final VendorRepository vendors;
    private final ServiceOrderAssembler assembler;

    @Transactional(readOnly = true)
    public ServiceOrderResponse get(UUID orgId, UUID id) {
        ServiceOrder order = orders.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("service order", id));
        return assembler.toResponse(orgId, order);
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceOrderSummaryResponse> list(UUID orgId, String search, ServiceOrderStatus status,
                                                          Boolean paid, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        Page<ServiceOrder> page = orders.search(orgId, status, paid, term, pageable);
        List<ServiceOrder> rows = page.getContent();

        Map<UUID, String> carNumbers = index(
                cars.findByOrgIdAndIdIn(orgId, ids(rows, ServiceOrder::getCarId)), Car::getId, Car::getCarNumber);
        Map<UUID, String> customerNames = index(
                customers.findByOrgIdAndIdIn(orgId, ids(rows, ServiceOrder::getCustomerId)), Customer::getId, Customer::getName);
        Map<UUID, String> employeeNames = index(
                employees.findByOrgIdAndIdIn(orgId, ids(rows, ServiceOrder::getEmployeeId)), Employee::getId, Employee::getName);
        Map<UUID, String> vendorNames = index(
                vendors.findByOrgIdAndIdIn(orgId, ids(rows, ServiceOrder::getVendorId)), Vendor::getId, Vendor::getName);
        Map<UUID, List<ServiceOrderItem>> lines = items
                .findByServiceOrderIdInOrderByCreatedAtAscIdAsc(rows.stream().map(ServiceOrder::getId).toList())
                .stream().collect(Collectors.groupingBy(ServiceOrderItem::getServiceOrderId));

        return PageResponse.of(page.map(o -> ServiceOrderSummaryResponse.of(o,
                carNumbers.get(o.getCarId()),
                customerNames.get(o.getCustomerId()),
                employeeNames.get(o.getEmployeeId()),
                vendorNames.get(o.getVendorId()),
                lines.getOrDefault(o.getId(), List.of()))));
    }

    private static Set<UUID> ids(List<ServiceOrder> rows, Function<ServiceOrder, UUID> pick) {
        return rows.stream().map(pick).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    }

    private static <E> Map<UUID, String> index(List<E> entities, Function<E, UUID> key, Function<E, String> value) {
        return entities.stream().collect(Collectors.toMap(key, value));
    }
}
