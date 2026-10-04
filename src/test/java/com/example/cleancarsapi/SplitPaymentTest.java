package com.example.cleancarsapi;

import com.example.cleancarsapi.dto.DashboardResponse;
import com.example.cleancarsapi.dto.PaymentPlanRequest;
import com.example.cleancarsapi.dto.PaymentRequest;
import com.example.cleancarsapi.dto.ServiceOrderItemRequest;
import com.example.cleancarsapi.dto.ServiceOrderRequest;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.entity.PaymentPlan;
import com.example.cleancarsapi.entity.PaymentType;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.security.AuthenticatedUser;
import com.example.cleancarsapi.service.DashboardService;
import com.example.cleancarsapi.service.ServiceOrderCreateService;
import com.example.cleancarsapi.service.ServiceOrderDeleteService;
import com.example.cleancarsapi.service.ServiceOrderPaymentCreateService;
import com.example.cleancarsapi.service.ServiceOrderPaymentDeleteService;
import com.example.cleancarsapi.service.ServiceOrderPaymentUpdateService;
import com.example.cleancarsapi.service.ServiceOrderUpdateService;
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

/** One-time and split payments on a service order: the rules, the derived paid flag, and the dashboard money. */
@SpringBootTest
@ActiveProfiles("test-api")
class SplitPaymentTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired ServiceOrderCreateService createService;
    @Autowired ServiceOrderUpdateService updateService;
    @Autowired ServiceOrderDeleteService deleteService;
    @Autowired ServiceOrderPaymentCreateService addPayment;
    @Autowired ServiceOrderPaymentUpdateService editPayment;
    @Autowired ServiceOrderPaymentDeleteService removePayment;
    @Autowired DashboardService dashboard;

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
        jdbc.update("INSERT INTO organizations (id, name) VALUES (?, 'split-test')", bytes(orgId));
        jdbc.update("INSERT INTO users (id, name) VALUES (?, 'split-test')", bytes(userId));
        jdbc.update("INSERT INTO customers (id, org_id, name, phone) VALUES (?, ?, 'c', '1')", bytes(customerId), bytes(orgId));
        jdbc.update("INSERT INTO cars (id, org_id, customer_id, car_number) VALUES (?, ?, ?, 'T1')",
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

    private ServiceOrderResponse order(PaymentPlan plan, String price) {
        return createService.create(orgId, new ServiceOrderRequest(carId, null, null, null, null, null, plan, null, null,
                List.of(new ServiceOrderItemRequest(null, "svc", new BigDecimal(price), null, null, 1, null, null))));
    }

    private static PaymentRequest pay(String amount, PaymentType type) {
        return new PaymentRequest(amount == null ? null : new BigDecimal(amount), type, null);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void oneTimeTakesOneFullPaymentIgnoringTheBodyAmount() {
        ServiceOrderResponse o = order(PaymentPlan.ONE_TIME, "1000");
        assertFalse(o.paid());

        ServiceOrderResponse paid = addPayment.create(orgId, o.id(), pay("1", PaymentType.UPI));
        assertTrue(paid.paid());
        assertMoney("1000", paid.amountPaid());
        assertMoney("0", paid.amountRemaining());
        assertEquals(1, paid.payments().size());
        assertMoney("1000", paid.payments().getFirst().amount());

        ConflictException again = assertThrows(ConflictException.class,
                () -> addPayment.create(orgId, o.id(), pay(null, PaymentType.CASH)));
        assertEquals("one_time_already_paid", again.getCode());

        ServiceOrderResponse unpaid = removePayment.delete(orgId, o.id(), paid.payments().getFirst().id());
        assertFalse(unpaid.paid());
        assertMoney("1000", unpaid.amountRemaining());
    }

    @Test
    void splitTracksRunningRemainingAndBecomesPaidOnTheLastSplit() {
        ServiceOrderResponse o = order(PaymentPlan.SPLIT, "1000");

        ServiceOrderResponse r1 = addPayment.create(orgId, o.id(), pay("400", PaymentType.CASH));
        assertFalse(r1.paid());
        assertMoney("600", r1.amountRemaining());
        assertMoney("600", r1.payments().getFirst().remainingAfter());

        ServiceOrderResponse r2 = addPayment.create(orgId, o.id(), pay("250.50", PaymentType.UPI));
        assertMoney("349.50", r2.amountRemaining());
        assertEquals(2, r2.payments().size());
        assertMoney("349.50", r2.payments().get(1).remainingAfter());

        ConflictException over = assertThrows(ConflictException.class,
                () -> addPayment.create(orgId, o.id(), pay("349.51", PaymentType.CARD)));
        assertEquals("payment_exceeds_remaining", over.getCode());

        ServiceOrderResponse r3 = addPayment.create(orgId, o.id(), pay("349.50", PaymentType.CARD));
        assertTrue(r3.paid());
        assertMoney("0", r3.amountRemaining());

        // raising an earlier split is bounded by the other payments, and lowering reopens the order
        UUID first = r3.payments().stream().filter(p -> p.amount().compareTo(new BigDecimal("400")) == 0).findFirst().orElseThrow().id();
        assertThrows(ConflictException.class, () -> editPayment.update(orgId, o.id(), first, pay("400.01", PaymentType.CASH)));
        ServiceOrderResponse lowered = editPayment.update(orgId, o.id(), first, pay("300", PaymentType.CASH));
        assertFalse(lowered.paid());
        assertMoney("100", lowered.amountRemaining());
    }

    @Test
    void splitAmountIsRequiredAndPlanCannotRevertToOneTimeWithPayments() {
        ServiceOrderResponse o = order(PaymentPlan.SPLIT, "500");
        assertThrows(com.example.cleancarsapi.exception.BadRequestException.class,
                () -> addPayment.create(orgId, o.id(), pay(null, PaymentType.CASH)));

        addPayment.create(orgId, o.id(), pay("100", PaymentType.CASH));
        ConflictException revert = assertThrows(ConflictException.class,
                () -> updateService.setPaymentPlan(orgId, o.id(), new PaymentPlanRequest(PaymentPlan.ONE_TIME)));
        assertEquals("payment_plan_has_payments", revert.getCode());

        ServiceOrderResponse fresh = order(PaymentPlan.ONE_TIME, "500");
        assertEquals(PaymentPlan.SPLIT,
                updateService.setPaymentPlan(orgId, fresh.id(), new PaymentPlanRequest(PaymentPlan.SPLIT)).paymentPlan());
    }

    @Test
    void editingLinesRederivesPaidAgainstWhatWasReceived() {
        ServiceOrderResponse o = order(PaymentPlan.SPLIT, "1000");
        addPayment.create(orgId, o.id(), pay("1000", PaymentType.CASH));

        ServiceOrderResponse bigger = updateService.update(orgId, o.id(), new ServiceOrderRequest(null, null, null, null, null, null, null, null, null,
                List.of(new ServiceOrderItemRequest(null, "svc", new BigDecimal("1500"), null, null, 1, null, null))));
        assertFalse(bigger.paid());
        assertMoney("500", bigger.amountRemaining());
    }

    @Test
    void orderFormCanCarryPaymentsAndReplaceThem() {
        var create = new ServiceOrderRequest(carId, null, null, null, null, null, PaymentPlan.SPLIT,
                List.of(new com.example.cleancarsapi.dto.ServiceOrderPaymentLine(null, new BigDecimal("300"), PaymentType.CASH, null),
                        new com.example.cleancarsapi.dto.ServiceOrderPaymentLine(null, new BigDecimal("200"), PaymentType.UPI, null)),
                null, List.of(new ServiceOrderItemRequest(null, "svc", new BigDecimal("1000"), null, null, 1, null, null)));
        ServiceOrderResponse o = createService.create(orgId, create);
        assertEquals(2, o.payments().size());
        assertMoney("500", o.amountRemaining());

        // keep the first (by id), drop the second, add one; overshoot is rejected
        var keep = new com.example.cleancarsapi.dto.ServiceOrderPaymentLine(o.payments().getFirst().id(), new BigDecimal("300"), PaymentType.CASH, null);
        var over = new com.example.cleancarsapi.dto.ServiceOrderPaymentLine(null, new BigDecimal("700.01"), PaymentType.CARD, null);
        var items = List.of(new ServiceOrderItemRequest(null, "svc", new BigDecimal("1000"), null, null, 1, null, null));
        assertEquals("payment_exceeds_remaining", assertThrows(ConflictException.class, () -> updateService.update(orgId, o.id(),
                new ServiceOrderRequest(null, null, null, null, null, null, null, List.of(keep, over), null, items))).getCode());
        var ok = new com.example.cleancarsapi.dto.ServiceOrderPaymentLine(null, new BigDecimal("700"), PaymentType.CARD, null);
        ServiceOrderResponse u = updateService.update(orgId, o.id(),
                new ServiceOrderRequest(null, null, null, null, null, null, null, List.of(keep, ok), null, items));
        assertTrue(u.paid());
        assertEquals(o.payments().getFirst().id(), u.payments().stream().filter(p -> p.amount().compareTo(new BigDecimal("300")) == 0).findFirst().orElseThrow().id());

        // null leaves payments alone; an empty list clears them
        assertEquals(2, updateService.update(orgId, o.id(), new ServiceOrderRequest(null, null, null, null, null, null, null, null, null, items)).payments().size());
        assertFalse(updateService.update(orgId, o.id(), new ServiceOrderRequest(null, null, null, null, null, null, null, List.of(), null, items)).paid());
    }

    @Test
    void dashboardCountsOnlyTheReceivedShareAsPaid() {
        ServiceOrderResponse o = order(PaymentPlan.SPLIT, "1000");
        addPayment.create(orgId, o.id(), pay("250", PaymentType.UPI));

        DashboardResponse d = dashboard.get(orgId);
        assertEquals(1, d.servicesUnpaid());
        assertMoney("750", d.unpaidAmount());
        assertMoney("250", d.todayRevenue().paid());
        assertMoney("750", d.todayRevenue().unpaid());

        deleteService.delete(orgId, o.id());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE org_id = ?", Integer.class, bytes(orgId)));
    }
}
