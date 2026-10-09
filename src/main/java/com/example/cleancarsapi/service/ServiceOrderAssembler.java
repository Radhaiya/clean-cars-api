package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CarServiceSummary;
import com.example.cleancarsapi.dto.TaxBreakdown;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.dto.ServiceOrderVehicle;
import com.example.cleancarsapi.dto.VehicleHistory;
import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.Invoice;
import com.example.cleancarsapi.entity.BikeBrand;
import com.example.cleancarsapi.entity.BikeModel;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.CarBrand;
import com.example.cleancarsapi.entity.CarModel;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import com.example.cleancarsapi.entity.Vendor;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.AmcSubscriptionRepository;
import com.example.cleancarsapi.repository.BikeBrandRepository;
import com.example.cleancarsapi.repository.BikeModelRepository;
import com.example.cleancarsapi.repository.CarBrandRepository;
import com.example.cleancarsapi.repository.CarModelRepository;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import com.example.cleancarsapi.repository.EmployeeRepository;
import com.example.cleancarsapi.repository.InvoiceRepository;
import com.example.cleancarsapi.repository.PaymentRepository;
import com.example.cleancarsapi.repository.ServiceOrderItemRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import com.example.cleancarsapi.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.UUID;
/** Builds read views over service orders — a full single order, and a vehicle's history. */
@Component
@RequiredArgsConstructor
public class ServiceOrderAssembler {

    private final ServiceOrderRepository orders;
    private final CarRepository cars;
    private final CarBrandRepository carBrands;
    private final CarModelRepository carModels;
    private final BikeBrandRepository bikeBrands;
    private final BikeModelRepository bikeModels;
    private final BikeRepository bikes;
    private final CustomerRepository customers;
    private final EmployeeRepository employees;
    private final VendorRepository vendors;
    private final ServiceOrderItemRepository items;
    private final PaymentRepository payments;
    private final AmcSubscriptionRepository amcSubscriptions;
    private final InvoiceRepository invoices;

    public ServiceOrderResponse toResponse(UUID orgId, ServiceOrder order) {
        Car car = order.getCarId() == null ? null : cars.findByIdAndOrgId(order.getCarId(), orgId).orElse(null);
        Bike bike = order.getBikeId() == null ? null : bikes.findByIdAndOrgId(order.getBikeId(), orgId).orElse(null);
        Customer customer = customers.findByIdAndOrgId(order.getCustomerId(), orgId).orElse(null);
        String customerName = customer == null ? null : customer.getName();
        String customerPhone = customer == null ? null : customer.getPhone();
        String employeeName = order.getEmployeeId() == null ? null
                : employees.findByIdAndOrgId(order.getEmployeeId(), orgId).map(Employee::getName).orElse(null);
        String vendorName = order.getVendorId() == null ? null
                : vendors.findByIdAndOrgId(order.getVendorId(), orgId).map(Vendor::getName).orElse(null);

        boolean customerDeleted = customer != null && customer.isDeleted();
        return ServiceOrderResponse.of(order, car == null ? null : car.getCarNumber(),
                bike == null ? null : bike.getBikeNumber(), customerName, customerPhone, customerDeleted,
                vehicleOf(orgId, car, bike), employeeName, vendorName, items.findByServiceOrderIdOrderByCreatedAtAscIdAsc(order.getId()),
                payments.findByServiceOrderIdOrderByPaymentDateAscCreatedAtAscIdAsc(order.getId()),
                amcOf(orgId, order),
                invoices.findByServiceOrderIdAndOrgId(order.getId(), orgId).map(Invoice::getId).orElse(null));
    }

    /** The AMC a redemption order used, with its 1-based use number; null for a normal order. */
    private ServiceOrderResponse.Amc amcOf(UUID orgId, ServiceOrder order) {
        if (!order.isAmcRedemption()) {
            return null;
        }
        return amcSubscriptions.findByIdAndOrgId(order.getAmcSubscriptionId(), orgId)
                .map(s -> new ServiceOrderResponse.Amc(s.getId(), s.getPlanName(),
                        (order.getAmcSlotIndex() == null ? 0 : order.getAmcSlotIndex()) + 1, s.totalSlots()))
                .orElse(null);
    }

    /** The order's vehicle with brand/model names — still resolves when the vehicle is soft-deleted. */
    public ServiceOrderVehicle vehicleOf(UUID orgId, Car car, Bike bike) {
        if (car != null) {
            return new ServiceOrderVehicle(car.getId(), "CAR", car.getCarNumber(),
                    car.getBrandId() == null ? null
                            : carBrands.findByIdAndOrgId(car.getBrandId(), orgId).map(CarBrand::getName).orElse(null),
                    car.getModelId() == null ? null
                            : carModels.findByIdAndOrgId(car.getModelId(), orgId).map(CarModel::getName).orElse(null),
                    car.getYear(), car.getColor(),
                    car.getFuelType() == null ? null : car.getFuelType().name(), car.isDeleted());
        }
        if (bike != null) {
            return new ServiceOrderVehicle(bike.getId(), "BIKE", bike.getBikeNumber(),
                    bike.getBrandId() == null ? null
                            : bikeBrands.findByIdAndOrgId(bike.getBrandId(), orgId).map(BikeBrand::getName).orElse(null),
                    bike.getModelId() == null ? null
                            : bikeModels.findByIdAndOrgId(bike.getModelId(), orgId).map(BikeModel::getName).orElse(null),
                    bike.getYear(), bike.getColor(),
                    bike.getFuelType() == null ? null : bike.getFuelType().name(), bike.isDeleted());
        }
        return null;
    }

    /**
     * A car's past service orders, newest first — gross total, paid, status, assignee, date —
     * plus the header stats (all computed over non-cancelled orders only).
     */
    public VehicleHistory historyForCar(UUID orgId, UUID carId) {
        return history(orgId, orders.findByOrgIdAndCarIdOrderByCreatedAtDesc(orgId, carId));
    }

    /** A bike's past service orders, newest first — same summary shape and stats as the car history. */
    public VehicleHistory historyForBike(UUID orgId, UUID bikeId) {
        return history(orgId, orders.findByOrgIdAndBikeIdOrderByCreatedAtDesc(orgId, bikeId));
    }

    private VehicleHistory history(UUID orgId, List<ServiceOrder> history) {
        if (history.isEmpty()) {
            return VehicleHistory.empty();
        }

        Map<UUID, List<ServiceOrderItem>> linesByOrder = items
                .findByServiceOrderIdInOrderByCreatedAtAscIdAsc(history.stream().map(ServiceOrder::getId).toList())
                .stream().collect(Collectors.groupingBy(ServiceOrderItem::getServiceOrderId));
        Map<UUID, String> employeeNames = employees
                .findByOrgIdAndIdIn(orgId, history.stream()
                        .map(ServiceOrder::getEmployeeId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Employee::getId, Employee::getName));

        List<CarServiceSummary> summaries = history.stream().map(o -> {
            TaxBreakdown total = linesByOrder.getOrDefault(o.getId(), List.of()).stream()
                    .map(i -> TaxBreakdown.ofLine(i))
                    .reduce(TaxBreakdown.zero(), TaxBreakdown::plus);
            return new CarServiceSummary(o.getId(), total.gross(), o.isPaid(), o.getAmountPaid(), o.getStatus(),
                    o.getEmployeeId(), employeeNames.get(o.getEmployeeId()), o.getCreatedAt());
        }).toList();

        List<CarServiceSummary> countable = summaries.stream()
                .filter(s -> s.status() != ServiceOrderStatus.CANCELLED)
                .toList();
        BigDecimal totalRevenue = countable.stream()
                .map(CarServiceSummary::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Integer lastOdometer = history.stream()
                .filter(o -> o.getStatus() != ServiceOrderStatus.CANCELLED)
                .map(ServiceOrder::getOdometerReading)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return VehicleHistory.of(summaries, countable.size(), totalRevenue, lastOdometer);
    }
}
