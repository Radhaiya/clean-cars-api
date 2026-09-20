package com.example.cleancarsapi;

import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Timestamps are UTC end to end: the app JVM is pinned to UTC
 * ({@code CleanCarsApiApplication}) and the DB stores UTC, so an insert writes
 * the current UTC wall time and reads it back unshifted.
 */
@SpringBootTest
@ActiveProfiles("test")
class UtcTimestampTest {

    @Autowired ServiceOrderRepository serviceOrders;
    @Autowired JdbcTemplate jdbc;

    @Test
    void storesAndReadsBackUtcWallTime() {
        assertEquals("UTC", ZoneId.systemDefault().getId());

        LocalDateTime before = LocalDateTime.now();
        ServiceOrder order = new ServiceOrder();
        order.setOrgId(1L);
        order.setCarId(1L);
        order.setCustomerId(2L);
        order.setCreatedBy(1L);
        order = serviceOrders.save(order);

        LocalDateTime storedUtc = jdbc.queryForObject(
                "SELECT created_at FROM service_orders WHERE id = ?",
                LocalDateTime.class, order.getId());
        LocalDateTime readBack = serviceOrders.findById(order.getId()).orElseThrow().getCreatedAt();

        // Stored value is "now" in UTC (not shifted by the machine's zone)...
        assertFalse(storedUtc.isBefore(before.minusSeconds(5)),
                "stored " + storedUtc + " should be >= " + before);
        assertFalse(storedUtc.isAfter(LocalDateTime.now().plusSeconds(5)),
                "stored " + storedUtc + " should be <= now");
        // ...and Hibernate reads back exactly the stored value, no skew.
        assertEquals(storedUtc.truncatedTo(ChronoUnit.SECONDS), readBack.truncatedTo(ChronoUnit.SECONDS),
                "read-back must equal the stored UTC value");

        serviceOrders.deleteById(order.getId());
    }
}
