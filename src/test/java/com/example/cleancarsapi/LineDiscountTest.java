package com.example.cleancarsapi;

import com.example.cleancarsapi.dto.ServiceOrderItemRequest;
import com.example.cleancarsapi.dto.ServiceOrderItemResponse;
import com.example.cleancarsapi.dto.ServiceOrderRequest;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.security.AuthenticatedUser;
import com.example.cleancarsapi.service.ServiceOrderCreateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Per-unit line discount (stored as an amount): totals, derived percent, tax-included lines, free orders. */
@SpringBootTest
@ActiveProfiles("test-api")
class LineDiscountTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired ServiceOrderCreateService createService;

    private UUID orgId;
    private UUID userId;
    private UUID carId;

    private static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    @BeforeEach
    void fixture() {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        carId = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, name) VALUES (?, 'disc-test')", bytes(orgId));
        jdbc.update("INSERT INTO users (id, name) VALUES (?, 'disc-test')", bytes(userId));
        jdbc.update("INSERT INTO customers (id, org_id, name, phone) VALUES (?, ?, 'c', '1')", bytes(customerId), bytes(orgId));
        jdbc.update("INSERT INTO cars (id, org_id, customer_id, car_number) VALUES (?, ?, ?, 'D1')",
                bytes(carId), bytes(orgId), bytes(customerId));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, orgId, UserRole.ADMIN, "t@t", "t"), null, List.of()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        byte[] org = bytes(orgId);
        jdbc.update("DELETE FROM payments WHERE org_id = ?", org);
        jdbc.update("DELETE FROM service_order_items WHERE service_order_id IN (SELECT id FROM service_orders WHERE org_id = ?)", org);
        jdbc.update("DELETE FROM service_orders WHERE org_id = ?", org);
        jdbc.update("DELETE FROM cars WHERE org_id = ?", org);
        jdbc.update("DELETE FROM customers WHERE org_id = ?", org);
        jdbc.update("DELETE FROM users WHERE id = ?", bytes(userId));
        jdbc.update("DELETE FROM organizations WHERE id = ?", org);
    }

    private ServiceOrderResponse order(String price, String tax, boolean included, int qty, String discount) {
        return createService.create(orgId, new ServiceOrderRequest(carId, null, null, null, null, null, null, null, null,
                List.of(new ServiceOrderItemRequest(null, "svc", new BigDecimal(price),
                        tax == null ? null : new BigDecimal(tax), included, qty,
                        discount == null ? null : new BigDecimal(discount), null))));
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void noDiscountLeavesTotalsUnchanged() {
        ServiceOrderResponse o = order("1000", "18", false, 1, null);
        assertMoney("1180", o.grossTotal());
        ServiceOrderItemResponse line = o.items().get(0);
        assertMoney("0", line.discountAmount());
        assertMoney("0", line.discountPercent());
    }

    @Test
    void discountComesOffThePriceBeforeTaxOnTaxExcludedLines() {
        // 1000 - 100 = 900 net, +18% tax = 1062 gross
        ServiceOrderResponse o = order("1000", "18", false, 1, "100");
        assertMoney("900", o.netTotal());
        assertMoney("162", o.taxTotal());
        assertMoney("1062", o.grossTotal());
        assertMoney("100", o.items().get(0).discountAmount());
        assertMoney("10", o.items().get(0).discountPercent());
    }

    @Test
    void discountComesOffTheDisplayedGrossOnTaxIncludedLines() {
        // 118 (18% included) - 11.80 = 106.20 gross
        ServiceOrderResponse o = order("118", "18", true, 1, "11.80");
        assertMoney("106.20", o.grossTotal());
        assertMoney("10", o.items().get(0).discountPercent());
    }

    @Test
    void discountIsPerUnitSoPercentIsIndependentOfQuantity() {
        ServiceOrderResponse o = order("500", null, false, 3, "50");
        assertMoney("1350", o.grossTotal()); // 3 x 450
        assertMoney("10", o.items().get(0).discountPercent());
    }

    @Test
    void discountCannotExceedThePrice() {
        assertThrows(BadRequestException.class, () -> order("500", null, false, 1, "500.01"));
    }

    @Test
    void fullDiscountIsFreeIncludingTaxAndTheOrderIsPaid() {
        ServiceOrderResponse o = order("1000", "18", false, 1, "1000");
        assertMoney("0", o.netTotal());
        assertMoney("0", o.taxTotal());
        assertMoney("0", o.grossTotal());
        assertMoney("100", o.items().get(0).discountPercent());
        assertTrue(o.paid(), "a zero-total order with lines is settled automatically");
        assertMoney("0", o.amountRemaining());
    }

    @Test
    void partiallyDiscountedOrderIsStillUnpaid() {
        assertFalse(order("1000", null, false, 1, "400").paid());
    }
}
