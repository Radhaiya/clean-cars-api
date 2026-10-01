package com.example.cleancarsapi;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.ServiceOrderItemRequest;
import com.example.cleancarsapi.dto.ServiceOrderRequest;
import com.example.cleancarsapi.dto.ServiceOrderSummaryResponse;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.repository.ServiceOrderSpecs.VehicleType;
import com.example.cleancarsapi.security.AuthenticatedUser;
import com.example.cleancarsapi.service.ServiceOrderCreateService;
import com.example.cleancarsapi.service.ServiceOrderReadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Column filters and sorts on the service-order list. */
@SpringBootTest
@ActiveProfiles("test")
class ServiceOrderListFilterTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired ServiceOrderCreateService createService;
    @Autowired ServiceOrderReadService readService;

    private UUID orgId;
    private UUID userId;
    private UUID carId;
    private UUID bikeId;
    private UUID ravi;
    private UUID amit;
    private UUID zed;

    private static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    @BeforeEach
    void fixture() {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        carId = UUID.randomUUID();
        bikeId = UUID.randomUUID();
        ravi = UUID.randomUUID();
        amit = UUID.randomUUID();
        zed = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, name) VALUES (?, 'filter-test')", bytes(orgId));
        jdbc.update("INSERT INTO users (id, name) VALUES (?, 'filter-test')", bytes(userId));
        jdbc.update("INSERT INTO customers (id, org_id, name, phone) VALUES (?, ?, 'Amit', '1')", bytes(amit), bytes(orgId));
        jdbc.update("INSERT INTO customers (id, org_id, name, phone) VALUES (?, ?, 'Zed', '2')", bytes(zed), bytes(orgId));
        jdbc.update("INSERT INTO cars (id, org_id, customer_id, car_number) VALUES (?, ?, ?, 'MH01')", bytes(carId), bytes(orgId), bytes(amit));
        jdbc.update("INSERT INTO bikes (id, org_id, customer_id, bike_number) VALUES (?, ?, ?, 'KA09')", bytes(bikeId), bytes(orgId), bytes(zed));
        jdbc.update("INSERT INTO employees (id, org_id, name) VALUES (?, ?, 'Ravi')", bytes(ravi), bytes(orgId));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, orgId, UserRole.ADMIN, "t@t", "t"), null, List.of()));

        // car: Ravi, 1 line @ 100 (18% tax excluded -> 118). bike: unassigned, 2 lines of 500 (no tax) -> 1000.
        createService.create(orgId, new ServiceOrderRequest(carId, null, ravi, null, null, null, null, null, null,
                List.of(new ServiceOrderItemRequest(null, "wash", new BigDecimal("100"), new BigDecimal("18"), false, 1, null, null))));
        createService.create(orgId, new ServiceOrderRequest(null, bikeId, null, null, null, null, null, null, null,
                List.of(new ServiceOrderItemRequest(null, "a", new BigDecimal("500"), null, null, 1, null, null),
                        new ServiceOrderItemRequest(null, "b", new BigDecimal("500"), null, null, 1, null, null))));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        byte[] org = bytes(orgId);
        jdbc.update("DELETE FROM service_order_items WHERE service_order_id IN (SELECT id FROM service_orders WHERE org_id = ?)", org);
        jdbc.update("DELETE FROM service_orders WHERE org_id = ?", org);
        jdbc.update("DELETE FROM cars WHERE org_id = ?", org);
        jdbc.update("DELETE FROM bikes WHERE org_id = ?", org);
        jdbc.update("DELETE FROM employees WHERE org_id = ?", org);
        jdbc.update("DELETE FROM customers WHERE org_id = ?", org);
        jdbc.update("DELETE FROM users WHERE id = ?", bytes(userId));
        jdbc.update("DELETE FROM organizations WHERE id = ?", org);
    }

    private List<String> list(String vehicle, VehicleType type, UUID customer, UUID employee, Integer services,
                              String min, String max, String sort, Sort.Direction dir) {
        Pageable p = PageRequest.of(0, 20, sort == null ? Sort.unsorted() : Sort.by(dir, sort));
        PageResponse<ServiceOrderSummaryResponse> r = readService.list(orgId, null, null, null, vehicle, type, customer,
                employee, services, min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max),
                null, null, p);
        return r.content().stream()
                .map(o -> o.carNumber() != null ? o.carNumber() : o.bikeNumber()).toList();
    }

    @Test
    void filtersNarrowTheList() {
        assertEquals(List.of("KA09", "MH01").size(), list(null, null, null, null, null, null, null, null, null).size());
        assertEquals(List.of("MH01"), list("mh", null, null, null, null, null, null, null, null));
        assertEquals(List.of("KA09"), list(null, VehicleType.BIKE, null, null, null, null, null, null, null));
        assertEquals(List.of("MH01"), list(null, VehicleType.CAR, null, null, null, null, null, null, null));
        assertEquals(List.of("KA09"), list(null, null, zed, null, null, null, null, null, null));
        assertEquals(List.of("MH01"), list(null, null, null, ravi, null, null, null, null, null));
        assertEquals(List.of("KA09"), list(null, null, null, null, 2, null, null, null, null));
        assertEquals(List.of("KA09"), list(null, null, null, null, null, "500", null, null, null));
        assertEquals(List.of("MH01"), list(null, null, null, null, null, null, "200", null, null));
        assertEquals(List.of("MH01"), list(null, null, null, null, null, "118", "118", null, null));
    }

    @Test
    void filtersCombineWithAnd() {
        // each matches an order on its own, but never the same one
        assertEquals(List.of(), list(null, null, zed, ravi, null, null, null, null, null));
        assertEquals(List.of(), list("MH01", null, zed, null, null, null, null, null, null));
        // neither exists
        assertEquals(List.of(), list(null, null, UUID.randomUUID(), UUID.randomUUID(), null, null, null, "status", Sort.Direction.DESC));
        assertEquals(List.of("MH01"), list("MH", null, amit, ravi, 1, "100", "200", null, null));
    }

    @Test
    void columnSortsOrderBothWays() {
        assertEquals(List.of("KA09", "MH01"), list(null, null, null, null, null, null, null, "vehicle", Sort.Direction.ASC));
        assertEquals(List.of("MH01", "KA09"), list(null, null, null, null, null, null, null, "vehicle", Sort.Direction.DESC));
        assertEquals(List.of("MH01", "KA09"), list(null, null, null, null, null, null, null, "customer", Sort.Direction.ASC));
        assertEquals(List.of("MH01", "KA09"), list(null, null, null, null, null, null, null, "total", Sort.Direction.ASC));
        assertEquals(List.of("KA09", "MH01"), list(null, null, null, null, null, null, null, "services", Sort.Direction.DESC));
        assertEquals(2, list(null, null, null, null, null, null, null, "employee", Sort.Direction.ASC).size());
        assertEquals(2, list(null, null, null, null, null, null, null, "bogus", Sort.Direction.ASC).size());
    }
}
