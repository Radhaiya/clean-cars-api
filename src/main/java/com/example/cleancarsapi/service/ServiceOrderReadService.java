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
    public ServiceOrderResponse get(long orgId, long id) {
        ServiceOrder order = orders.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("service order", id));
        return assembler.toResponse(orgId, order);
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceOrderSummaryResponse> list(long orgId, String search, ServiceOrderStatus status,
                                                          Boolean paid, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        Page<ServiceOrder> page = orders.search(orgId, status, paid, term, pageable);
        List<ServiceOrder> rows = page.getContent();

        Map<Long, String> carNumbers = index(
                cars.findByOrgIdAndIdIn(orgId, ids(rows, ServiceOrder::getCarId)), Car::getId, Car::getCarNumber);
        Map<Long, String> customerNames = index(
                customers.findByOrgIdAndIdIn(orgId, ids(rows, ServiceOrder::getCustomerId)), Customer::getId, Customer::getName);
        Map<Long, String> employeeNames = index(
                employees.findByOrgIdAndIdIn(orgId, ids(rows, ServiceOrder::getEmployeeId)), Employee::getId, Employee::getName);
        Map<Long, String> vendorNames = index(
                vendors.findByOrgIdAndIdIn(orgId, ids(rows, ServiceOrder::getVendorId)), Vendor::getId, Vendor::getName);
        Map<Long, List<ServiceOrderItem>> lines = items
                .findByServiceOrderIdInOrderByIdAsc(rows.stream().map(ServiceOrder::getId).toList())
                .stream().collect(Collectors.groupingBy(ServiceOrderItem::getServiceOrderId));

        return PageResponse.of(page.map(o -> ServiceOrderSummaryResponse.of(o,
                carNumbers.get(o.getCarId()),
                customerNames.get(o.getCustomerId()),
                employeeNames.get(o.getEmployeeId()),
                vendorNames.get(o.getVendorId()),
                lines.getOrDefault(o.getId(), List.of()))));
    }

    private static Set<Long> ids(List<ServiceOrder> rows, Function<ServiceOrder, Long> pick) {
        return rows.stream().map(pick).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    }

    private static <E> Map<Long, String> index(List<E> entities, Function<E, Long> key, Function<E, String> value) {
        return entities.stream().collect(Collectors.toMap(key, value));
    }
}
