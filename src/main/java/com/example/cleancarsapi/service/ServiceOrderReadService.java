package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.OrgTimeZoneResolver;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.dto.ServiceOrderSummaryResponse;
import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import com.example.cleancarsapi.entity.Vendor;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import com.example.cleancarsapi.repository.EmployeeRepository;
import com.example.cleancarsapi.repository.ServiceOrderItemRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import com.example.cleancarsapi.repository.ServiceOrderSpecs;
import com.example.cleancarsapi.repository.ServiceOrderSpecs.Filter;
import com.example.cleancarsapi.repository.ServiceOrderSpecs.VehicleType;
import com.example.cleancarsapi.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
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
    private final BikeRepository bikes;
    private final CustomerRepository customers;
    private final EmployeeRepository employees;
    private final VendorRepository vendors;
    private final ServiceOrderAssembler assembler;
    private final OrgTimeZoneResolver orgTimezones;

    @Transactional(readOnly = true)
    public ServiceOrderResponse get(UUID orgId, UUID id) {
        ServiceOrder order = orders.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("service order", id));
        return assembler.toResponse(orgId, order);
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceOrderSummaryResponse> list(UUID orgId, String search, ServiceOrderStatus status,
                                                          Boolean paid, String vehicle, VehicleType vehicleType,
                                                          UUID customerId, UUID employeeId, Integer services,
                                                          BigDecimal totalMin, BigDecimal totalMax,
                                                          LocalDate from, LocalDate to, Pageable pageable) {
        ZoneId zone = orgTimezones.zone();
        Filter filter = new Filter(status, paid, blankToNull(search), blankToNull(vehicle), vehicleType,
                customerId, employeeId, services, totalMin, totalMax,
                from == null ? null : toUtc(from.atStartOfDay(), zone),
                to == null ? null : toUtc(to.plusDays(1).atStartOfDay(), zone));

        // Column sorts (vehicle/customer/…) are applied inside the spec; plain entity fields go through the pageable.
        Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
        String column = order != null && ServiceOrderSpecs.isColumnSort(order.getProperty()) ? order.getProperty() : null;
        Pageable paging = column != null ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortableOrDefault(pageable.getSort()));
        Page<ServiceOrder> page = orders.findAll(
                ServiceOrderSpecs.of(orgId, filter, column, order == null || order.isAscending()), paging);
        List<ServiceOrder> rows = page.getContent();

        List<Car> carRows = cars.findByOrgIdAndIdIn(orgId, ids(rows, ServiceOrder::getCarId));
        List<Bike> bikeRows = bikes.findByOrgIdAndIdIn(orgId, ids(rows, ServiceOrder::getBikeId));
        List<Customer> customerRows = customers.findByOrgIdAndIdIn(orgId, ids(rows, ServiceOrder::getCustomerId));
        Map<UUID, String> carNumbers = index(carRows, Car::getId, Car::getCarNumber);
        Map<UUID, String> bikeNumbers = index(bikeRows, Bike::getId, Bike::getBikeNumber);
        Map<UUID, String> customerNames = index(customerRows, Customer::getId, Customer::getName);
        Set<UUID> deletedVehicles = new HashSet<>(ids(carRows.stream().filter(Car::isDeleted).map(Car::getId)));
        deletedVehicles.addAll(ids(bikeRows.stream().filter(Bike::isDeleted).map(Bike::getId)));
        Set<UUID> deletedCustomers = ids(customerRows.stream().filter(Customer::isDeleted).map(Customer::getId));
        Map<UUID, String> employeeNames = index(
                employees.findByOrgIdAndIdIn(orgId, ids(rows, ServiceOrder::getEmployeeId)), Employee::getId, Employee::getName);
        Map<UUID, String> vendorNames = index(
                vendors.findByOrgIdAndIdIn(orgId, ids(rows, ServiceOrder::getVendorId)), Vendor::getId, Vendor::getName);
        Map<UUID, List<ServiceOrderItem>> lines = items
                .findByServiceOrderIdInOrderByCreatedAtAscIdAsc(rows.stream().map(ServiceOrder::getId).toList())
                .stream().collect(Collectors.groupingBy(ServiceOrderItem::getServiceOrderId));

        return PageResponse.of(page.map(o -> ServiceOrderSummaryResponse.of(o,
                carNumbers.get(o.getCarId()),
                bikeNumbers.get(o.getBikeId()),
                customerNames.get(o.getCustomerId()),
                deletedCustomers.contains(o.getCustomerId()),
                deletedVehicles.contains(o.getCarId() != null ? o.getCarId() : o.getBikeId()),
                employeeNames.get(o.getEmployeeId()),
                vendorNames.get(o.getVendorId()),
                lines.getOrDefault(o.getId(), List.of()))));
    }

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "status", "paid");

    /** Only the allowlisted entity fields may reach the query; anything else falls back to newest-first. */
    private static Sort sortableOrDefault(Sort requested) {
        boolean ok = requested.isSorted() && requested.stream().allMatch(o -> SORTABLE_FIELDS.contains(o.getProperty()));
        return ok ? requested : Sort.by(Sort.Direction.DESC, "createdAt");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static LocalDateTime toUtc(LocalDateTime orgWall, ZoneId zone) {
        return orgWall.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static Set<UUID> ids(List<ServiceOrder> rows, Function<ServiceOrder, UUID> pick) {
        return rows.stream().map(pick).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    }

    private static Set<UUID> ids(java.util.stream.Stream<UUID> stream) {
        return stream.collect(Collectors.toSet());
    }

    private static <E> Map<UUID, String> index(List<E> entities, Function<E, UUID> key, Function<E, String> value) {
        return entities.stream().collect(Collectors.toMap(key, value));
    }
}
