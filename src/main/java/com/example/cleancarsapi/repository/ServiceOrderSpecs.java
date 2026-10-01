package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filters and column sorts for the service-order list. Customer / vehicle / employee names and the
 * line-item total live in other tables (orders only hold their ids), so both the filters and the
 * sorts go through correlated subqueries.
 */
public final class ServiceOrderSpecs {

    private ServiceOrderSpecs() {
    }

    public enum VehicleType { CAR, BIKE }

    /** Every list filter; null means "don't filter on this". Dates are UTC instants (already zone-converted). */
    public record Filter(ServiceOrderStatus status, Boolean paid, String search,
                         String vehicle, VehicleType vehicleType, UUID customerId, UUID employeeId,
                         Integer services, BigDecimal totalMin, BigDecimal totalMax,
                         LocalDateTime from, LocalDateTime toExclusive) {
    }

    /** UI column key → how to sort by it. Anything else falls back to the pageable's own entity-field sort. */
    public static boolean isColumnSort(String property) {
        return switch (property) {
            case "vehicle", "customer", "employee", "services", "total" -> true;
            default -> false;
        };
    }

    public static Specification<ServiceOrder> of(UUID orgId, Filter f, String sortColumn, boolean ascending) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            and.add(cb.equal(root.get("orgId"), orgId));
            if (f.status() != null) {
                and.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.paid() != null) {
                and.add(cb.equal(root.get("paid"), f.paid()));
            }
            if (f.search() != null) {
                and.add(cb.or(customerLike(root, query, cb, f.search()),
                        vehicleLike(root, query, cb, f.search(), null)));
            }
            if (f.vehicle() != null || f.vehicleType() != null) {
                and.add(vehicleLike(root, query, cb, f.vehicle(), f.vehicleType()));
            }
            if (f.customerId() != null) {
                and.add(cb.equal(root.get("customerId"), f.customerId()));
            }
            if (f.employeeId() != null) {
                and.add(cb.equal(root.get("employeeId"), f.employeeId()));
            }
            if (f.services() != null) {
                and.add(cb.equal(itemCount(root, query, cb), f.services().longValue()));
            }
            if (f.totalMin() != null) {
                and.add(cb.greaterThanOrEqualTo(total(root, query, cb), f.totalMin()));
            }
            if (f.totalMax() != null) {
                and.add(cb.lessThanOrEqualTo(total(root, query, cb), f.totalMax()));
            }
            if (f.from() != null) {
                and.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            }
            if (f.toExclusive() != null) {
                and.add(cb.lessThan(root.get("createdAt"), f.toExclusive()));
            }

            // The count query runs the same spec with a Long result type — ordering must be skipped there.
            if (sortColumn != null && query.getResultType() != Long.class) {
                Expression<?> key = switch (sortColumn) {
                    case "vehicle" -> cb.coalesce(carNumber(root, query, cb), bikeNumber(root, query, cb));
                    case "customer" -> cb.lower(customerName(root, query, cb));
                    case "employee" -> cb.lower(employeeName(root, query, cb));
                    case "services" -> itemCount(root, query, cb);
                    default -> total(root, query, cb);
                };
                query.orderBy(ascending ? cb.asc(key) : cb.desc(key), cb.desc(root.get("id")));
            }
            return cb.and(and.toArray(Predicate[]::new));
        };
    }

    private static Predicate like(CriteriaBuilder cb, Expression<String> field, String term) {
        return cb.like(cb.lower(field), "%" + term.toLowerCase() + "%");
    }

    private static Predicate customerLike(Root<ServiceOrder> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
                                          CriteriaBuilder cb, String term) {
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<Customer> c = sq.from(Customer.class);
        sq.select(cb.literal(1)).where(cb.equal(c.get("id"), root.get("customerId")), like(cb, c.get("name"), term));
        return cb.exists(sq);
    }

    /** Matches the car or bike number; {@code type} narrows to one kind (term may then be null = any number). */
    private static Predicate vehicleLike(Root<ServiceOrder> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
                                         CriteriaBuilder cb, String term, VehicleType type) {
        List<Predicate> kinds = new ArrayList<>();
        if (type != VehicleType.BIKE) {
            Subquery<Integer> sq = query.subquery(Integer.class);
            Root<Car> car = sq.from(Car.class);
            List<Predicate> w = new ArrayList<>(List.of(cb.equal(car.get("id"), root.get("carId"))));
            if (term != null) {
                w.add(like(cb, car.get("carNumber"), term));
            }
            sq.select(cb.literal(1)).where(w.toArray(Predicate[]::new));
            kinds.add(cb.exists(sq));
        }
        if (type != VehicleType.CAR) {
            Subquery<Integer> sq = query.subquery(Integer.class);
            Root<Bike> bike = sq.from(Bike.class);
            List<Predicate> w = new ArrayList<>(List.of(cb.equal(bike.get("id"), root.get("bikeId"))));
            if (term != null) {
                w.add(like(cb, bike.get("bikeNumber"), term));
            }
            sq.select(cb.literal(1)).where(w.toArray(Predicate[]::new));
            kinds.add(cb.exists(sq));
        }
        return cb.or(kinds.toArray(Predicate[]::new));
    }

    private static Expression<String> carNumber(Root<ServiceOrder> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
                                                CriteriaBuilder cb) {
        Subquery<String> sq = query.subquery(String.class);
        Root<Car> c = sq.from(Car.class);
        return sq.select(c.<String>get("carNumber")).where(cb.equal(c.get("id"), root.get("carId")));
    }

    private static Expression<String> bikeNumber(Root<ServiceOrder> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
                                                 CriteriaBuilder cb) {
        Subquery<String> sq = query.subquery(String.class);
        Root<Bike> b = sq.from(Bike.class);
        return sq.select(b.<String>get("bikeNumber")).where(cb.equal(b.get("id"), root.get("bikeId")));
    }

    private static Expression<String> customerName(Root<ServiceOrder> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
                                                   CriteriaBuilder cb) {
        Subquery<String> sq = query.subquery(String.class);
        Root<Customer> c = sq.from(Customer.class);
        return sq.select(c.<String>get("name")).where(cb.equal(c.get("id"), root.get("customerId")));
    }

    private static Expression<String> employeeName(Root<ServiceOrder> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
                                                   CriteriaBuilder cb) {
        Subquery<String> sq = query.subquery(String.class);
        Root<Employee> e = sq.from(Employee.class);
        return sq.select(e.<String>get("name")).where(cb.equal(e.get("id"), root.get("employeeId")));
    }

    private static Expression<Long> itemCount(Root<ServiceOrder> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
                                              CriteriaBuilder cb) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<ServiceOrderItem> i = sq.from(ServiceOrderItem.class);
        return sq.select(cb.count(i)).where(cb.equal(i.get("serviceOrderId"), root.get("id")));
    }

    /** Gross total: sum of qty × ((base − discount) if tax-included, else base × (1 + tax%)). Matches {@code TaxBreakdown} up to per-line 2dp rounding. */
    private static Expression<BigDecimal> total(Root<ServiceOrder> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
                                                CriteriaBuilder cb) {
        Subquery<BigDecimal> sq = query.subquery(BigDecimal.class);
        Root<ServiceOrderItem> i = sq.from(ServiceOrderItem.class);
        Expression<BigDecimal> base = cb.<BigDecimal>selectCase()
                .when(cb.greaterThan(i.<BigDecimal>get("discountAmount"), i.<BigDecimal>get("basePrice")), BigDecimal.ZERO)
                .otherwise(cb.diff(i.<BigDecimal>get("basePrice"), i.<BigDecimal>get("discountAmount")));
        Expression<BigDecimal> pct = cb.coalesce(i.<BigDecimal>get("taxPercentage"), BigDecimal.ZERO);
        Expression<BigDecimal> factor = cb.sum(cb.quot(pct, new BigDecimal("100")).as(BigDecimal.class), BigDecimal.ONE);
        Expression<BigDecimal> withTax = cb.prod(base, factor);
        Expression<BigDecimal> gross = cb.<BigDecimal>selectCase()
                .when(cb.isTrue(i.get("taxIncluded")), base).otherwise(withTax);
        Expression<BigDecimal> line = cb.prod(gross, i.<Integer>get("quantity")).as(BigDecimal.class);
        return cb.coalesce(sq.select(cb.sum(line)).where(cb.equal(i.get("serviceOrderId"), root.get("id"))), BigDecimal.ZERO);
    }
}
