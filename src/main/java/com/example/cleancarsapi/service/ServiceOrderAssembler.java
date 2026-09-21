package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CarServiceSummary;
import com.example.cleancarsapi.dto.GstBreakdown;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.entity.Vendor;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import com.example.cleancarsapi.repository.EmployeeRepository;
import com.example.cleancarsapi.repository.ServiceOrderItemRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import com.example.cleancarsapi.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.UUID;
/** Builds read views over service orders — a full single order, and a car's history. */
@Component
@RequiredArgsConstructor
public class ServiceOrderAssembler {

    private final ServiceOrderRepository orders;
    private final CarRepository cars;
    private final CustomerRepository customers;
    private final EmployeeRepository employees;
    private final VendorRepository vendors;
    private final ServiceOrderItemRepository items;

    public ServiceOrderResponse toResponse(UUID orgId, ServiceOrder order) {
        String carNumber = cars.findByIdAndOrgId(order.getCarId(), orgId).map(Car::getCarNumber).orElse(null);
        Customer customer = customers.findByIdAndOrgId(order.getCustomerId(), orgId).orElse(null);
        String customerName = customer == null ? null : customer.getName();
        String customerPhone = customer == null ? null : customer.getPhone();
        String employeeName = order.getEmployeeId() == null ? null
                : employees.findByIdAndOrgId(order.getEmployeeId(), orgId).map(Employee::getName).orElse(null);
        String vendorName = order.getVendorId() == null ? null
                : vendors.findByIdAndOrgId(order.getVendorId(), orgId).map(Vendor::getName).orElse(null);

        return ServiceOrderResponse.of(order, carNumber, customerName, customerPhone, employeeName, vendorName,
                items.findByServiceOrderIdOrderByCreatedAtAscIdAsc(order.getId()));
    }

    /** A car's past service orders, newest first — gross total, paid, status, assignee, date. */
    public List<CarServiceSummary> historyForCar(UUID orgId, UUID carId) {
        List<ServiceOrder> history = orders.findByOrgIdAndCarIdOrderByCreatedAtDesc(orgId, carId);
        if (history.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<ServiceOrderItem>> linesByOrder = items
                .findByServiceOrderIdInOrderByCreatedAtAscIdAsc(history.stream().map(ServiceOrder::getId).toList())
                .stream().collect(Collectors.groupingBy(ServiceOrderItem::getServiceOrderId));
        Map<UUID, String> employeeNames = employees
                .findByOrgIdAndIdIn(orgId, history.stream()
                        .map(ServiceOrder::getEmployeeId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Employee::getId, Employee::getName));

        return history.stream().map(o -> {
            GstBreakdown total = linesByOrder.getOrDefault(o.getId(), List.of()).stream()
                    .map(i -> GstBreakdown.of(i.getBasePrice(), i.getGstPercentage(), i.isGstIncluded()).times(i.getQuantity()))
                    .reduce(GstBreakdown.zero(), GstBreakdown::plus);
            return new CarServiceSummary(o.getId(), total.gross(), o.isPaid(), o.getStatus(),
                    o.getEmployeeId(), employeeNames.get(o.getEmployeeId()), o.getCreatedAt());
        }).toList();
    }
}
