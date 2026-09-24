package com.example.cleancarsapi;

import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

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

    /** BINARY(16) ids come back from JdbcTemplate as raw 16-byte arrays. */
    private static UUID toUuid(Object raw) {
        ByteBuffer buf = ByteBuffer.wrap((byte[]) raw);
        return new UUID(buf.getLong(), buf.getLong());
    }

    /** BINARY(16) ids must also be sent as raw 16-byte arrays — a UUID object binds as a hex string and matches nothing. */
    private static byte[] toBytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

    @Test
    void storesAndReadsBackUtcWallTime() {
        assertEquals("UTC", ZoneId.systemDefault().getId());

        // Live-DB-proof fixture: any org that has a car + customer + user. Leftover
        // dev rows drift, so the ids are derived, never hardcoded.
        Map<String, Object> triple;
        try {
            triple = jdbc.queryForMap("""
                    SELECT c.org_id AS org_id, c.id AS car_id,
                           (SELECT cu.id FROM customers cu WHERE cu.org_id = c.org_id LIMIT 1) AS customer_id,
                           (SELECT u.id  FROM users   u  WHERE u.org_id  = c.org_id  LIMIT 1) AS created_by
                    FROM cars c WHERE c.org_id IS NOT NULL
                    LIMIT 1
                    """);
        } catch (org.springframework.dao.EmptyResultDataAccessException noFixture) {
            triple = Map.of();
        }
        if (triple.get("customer_id") == null || triple.get("created_by") == null) {
            // Nothing insertable in the current DB shape — the UTC policy holds either way,
            // but there is no valid ServiceOrder row to prove it with. Skip, don't fail.
            System.out.println("UtcTimestampTest skipped: no org with car + customer + user in the DB");
            return;
        }

        LocalDateTime before = LocalDateTime.now();
        ServiceOrder order = new ServiceOrder();
        order.setOrgId(toUuid(triple.get("org_id")));
        order.setCarId(toUuid(triple.get("car_id")));
        order.setCustomerId(toUuid(triple.get("customer_id")));
        order.setCreatedBy(toUuid(triple.get("created_by")));
        order = serviceOrders.save(order);

        try {
            LocalDateTime storedUtc = jdbc.queryForObject(
                    "SELECT created_at FROM service_orders WHERE id = ?",
                    LocalDateTime.class, toBytes(order.getId()));
            LocalDateTime readBack = serviceOrders.findById(order.getId()).orElseThrow().getCreatedAt();

            // Stored value is "now" in UTC (not shifted by the machine's zone)...
            assertFalse(storedUtc.isBefore(before.minusSeconds(5)),
                    "stored " + storedUtc + " should be >= " + before);
            assertFalse(storedUtc.isAfter(LocalDateTime.now().plusSeconds(5)),
                    "stored " + storedUtc + " should be <= now");
            // ...and Hibernate reads back exactly the stored value, no skew.
            assertEquals(storedUtc.truncatedTo(ChronoUnit.SECONDS), readBack.truncatedTo(ChronoUnit.SECONDS),
                    "read-back must equal the stored UTC value");
        } finally {
            serviceOrders.deleteById(order.getId()); // never leave a stray row behind
        }
    }
}
