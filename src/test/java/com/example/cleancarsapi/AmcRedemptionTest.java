package com.example.cleancarsapi;

import com.example.cleancarsapi.dto.AmcPlanRequest;
import com.example.cleancarsapi.dto.AmcPlanResponse;
import com.example.cleancarsapi.dto.AmcSaleRequest;
import com.example.cleancarsapi.dto.AmcSubscriptionResponse;
import com.example.cleancarsapi.dto.AmcVariantRequest;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.PaymentType;
import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.repository.SubscriptionRepository;
import com.example.cleancarsapi.security.AuthenticatedUser;
import com.example.cleancarsapi.dto.AmcRedeemRequest;
import com.example.cleancarsapi.dto.PaymentRequest;
import com.example.cleancarsapi.dto.ServiceOrderItemRequest;
import com.example.cleancarsapi.dto.ServiceOrderRequest;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import com.example.cleancarsapi.service.AmcRedemptionService;
import com.example.cleancarsapi.service.ServiceOrderDeleteService;
import com.example.cleancarsapi.service.ServiceOrderPaymentCreateService;
import com.example.cleancarsapi.service.ServiceOrderUpdateService;
import com.example.cleancarsapi.service.AmcPlanCreateService;
import com.example.cleancarsapi.service.AmcPlanDeleteService;
import com.example.cleancarsapi.service.AmcPlanReadService;
import com.example.cleancarsapi.service.AmcPlanUpdateService;
import com.example.cleancarsapi.service.AmcSlots;
import com.example.cleancarsapi.service.AmcSubscriptionCreateService;
import com.example.cleancarsapi.service.AmcSubscriptionReadService;
import com.example.cleancarsapi.service.AmcVariantCreateService;
import com.example.cleancarsapi.service.AmcVariantUpdateService;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Redeeming an AMC: a locked ₹0 bundle order, strict slots, free-on-cancel, no reopen, no bill. Rules: docs/FEATURE-AMC.md. */
@SpringBootTest
@ActiveProfiles("test")
class AmcRedemptionTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired OrganizationRepository organizations;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired AmcPlanCreateService planCreate;
    @Autowired AmcPlanReadService planRead;
    @Autowired AmcPlanUpdateService planUpdate;
    @Autowired AmcPlanDeleteService planDelete;
    @Autowired AmcVariantCreateService variantCreate;
    @Autowired AmcVariantUpdateService variantUpdate;
    @Autowired AmcSubscriptionCreateService sell;
    @Autowired AmcSubscriptionReadService read;
    @Autowired AmcRedemptionService redemption;
    @Autowired ServiceOrderUpdateService orderUpdate;
    @Autowired ServiceOrderDeleteService orderDelete;
    @Autowired ServiceOrderPaymentCreateService addPayment;

    UUID planWithAmc;
    UUID planWithoutAmc;
    Organization org;
    Organization otherOrg;
    Organization noAmcOrg;
    UUID carId;
    UUID bikeId;
    UUID otherOrgCarId;
    UUID employeeId;
    final List<UUID> orgIds = new ArrayList<>();

    private static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    private Organization orgOn(UUID plan, String name) {
        Organization o = new Organization();
        o.setName(name);
        o.setTimezone("Asia/Kolkata");
        o.setCurrencyCode("INR");
        o.setCurrencySymbol("₹");
        o = organizations.save(o);
        Subscription sub = new Subscription();
        sub.setOrgId(o.getId());
        sub.setPlanId(plan);
        sub.setStatus(SubscriptionStatus.TRIALING);
        sub.setStartDate(LocalDate.now());
        sub.setEndDate(LocalDate.now().plusDays(5));
        subscriptions.save(sub);
        orgIds.add(o.getId());
        return o;
    }

    private UUID car(Organization o, String number) {
        UUID customer = UUID.randomUUID();
        UUID car = UUID.randomUUID();
        jdbc.update("INSERT INTO customers (id, org_id, name, phone) VALUES (?, ?, 'c', '1')", bytes(customer), bytes(o.getId()));
        jdbc.update("INSERT INTO cars (id, org_id, customer_id, car_number) VALUES (?, ?, ?, ?)",
                bytes(car), bytes(o.getId()), bytes(customer), number);
        return car;
    }

    @BeforeEach
    void setup() {
        planWithAmc = UUID.randomUUID();
        planWithoutAmc = UUID.randomUUID();
        jdbc.update("INSERT INTO subscription_plans (id, name, is_trial, max_users, invoice_generation, is_public, sort_order) "
                + "VALUES (?, 'SaleOn', FALSE, 5, FALSE, FALSE, 980)", bytes(planWithAmc));
        jdbc.update("INSERT INTO subscription_plans (id, name, is_trial, max_users, invoice_generation, amc_enabled, is_public, sort_order) "
                + "VALUES (?, 'SaleOff', FALSE, 5, FALSE, FALSE, FALSE, 981)", bytes(planWithoutAmc));
        org = orgOn(planWithAmc, "sale-org");
        otherOrg = orgOn(planWithAmc, "sale-other-org");
        noAmcOrg = orgOn(planWithoutAmc, "sale-off-org");
        carId = car(org, "S1");
        otherOrgCarId = car(otherOrg, "S2");
        UUID customer = UUID.randomUUID();
        bikeId = UUID.randomUUID();
        jdbc.update("INSERT INTO customers (id, org_id, name, phone) VALUES (?, ?, 'b', '2')", bytes(customer), bytes(org.getId()));
        jdbc.update("INSERT INTO bikes (id, org_id, customer_id, bike_number) VALUES (?, ?, ?, 'B1')",
                bytes(bikeId), bytes(org.getId()), bytes(customer));
        employeeId = UUID.randomUUID();
        jdbc.update("INSERT INTO employees (id, org_id, name) VALUES (?, ?, 'Seller')", bytes(employeeId), bytes(org.getId()));
        as(org, UserRole.OWNER);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        for (UUID id : orgIds) {
            byte[] o = bytes(id);
            jdbc.update("DELETE FROM service_order_items WHERE service_order_id IN (SELECT id FROM service_orders WHERE org_id = ?)", o);
            jdbc.update("DELETE FROM payments WHERE org_id = ?", o);
            jdbc.update("DELETE FROM service_orders WHERE org_id = ?", o);
            jdbc.update("DELETE FROM amc_subscription_items WHERE subscription_id IN (SELECT id FROM amc_subscriptions WHERE org_id = ?)", o);
            jdbc.update("DELETE FROM amc_subscriptions WHERE org_id = ?", o);
            jdbc.update("DELETE FROM amc_variant_rows WHERE variant_id IN (SELECT id FROM amc_plan_variants WHERE org_id = ?)", o);
            jdbc.update("DELETE FROM amc_plan_variants WHERE org_id = ?", o);
            jdbc.update("DELETE FROM amc_plan_items WHERE plan_id IN (SELECT id FROM amc_plans WHERE org_id = ?)", o);
            jdbc.update("DELETE FROM amc_plans WHERE org_id = ?", o);
            jdbc.update("DELETE FROM employees WHERE org_id = ?", o);
            jdbc.update("DELETE FROM cars WHERE org_id = ?", o);
            jdbc.update("DELETE FROM bikes WHERE org_id = ?", o);
            jdbc.update("DELETE FROM customers WHERE org_id = ?", o);
            jdbc.update("DELETE FROM subscriptions WHERE org_id = ?", o);
            jdbc.update("DELETE FROM organizations WHERE id = ?", o);
        }
        jdbc.update("DELETE FROM subscription_plans WHERE id IN (?, ?)", bytes(planWithAmc), bytes(planWithoutAmc));
    }

    private void as(Organization o, UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(UUID.randomUUID(), o.getId(), role, "t@t", "t"), null, List.of()));
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private static AmcVariantRequest variant(int tenure, int interval) {
        return new AmcVariantRequest(tenure, interval, List.of(
                new AmcVariantRequest.Row("Oil change", null, new BigDecimal("1000"), new BigDecimal("18"), false),
                new AmcVariantRequest.Row("Car wash", null, new BigDecimal("118"), new BigDecimal("18"), true)));
    }

    /** A 12-month, monthly "Gold" plan: bundle gross 1298, upfront 15576. Returns [planId, variantId]. */
    private UUID[] goldPlan() {
        AmcPlanResponse plan = planCreate.create(org.getId(), new AmcPlanRequest("Gold", List.of("Oil change", "Car wash")));
        AmcPlanResponse withVariant = variantCreate.create(org.getId(), plan.id(), variant(12, 1));
        return new UUID[]{plan.id(), withVariant.variants().get(0).id()};
    }

    private AmcSaleRequest saleOfCar(UUID variantId) {
        return new AmcSaleRequest(carId, null, variantId, null, null, null, PaymentType.CASH, null);
    }

    private static final AmcRedeemRequest PLAIN = new AmcRedeemRequest(null, null, null, null);

    private AmcSubscriptionResponse soldGold() {
        AmcPlanResponse plan = planCreate.create(org.getId(), new AmcPlanRequest("Gold", List.of("Oil change", "Car wash")));
        UUID variantId = variantCreate.create(org.getId(), plan.id(), variant(12, 1)).variants().get(0).id();
        return sell.create(org.getId(), new AmcSaleRequest(carId, null, variantId, null, null, null, PaymentType.CASH, null));
    }

    private void startedMonthsAgo(UUID subscriptionId, int months) {
        jdbc.update("UPDATE amc_subscriptions SET start_date = ? WHERE id = ?",
                LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).minusMonths(months), bytes(subscriptionId));
    }

    @Test
    void redeemingCreatesAFreeLockedBundleOrderThatIsAlreadyPaid() {
        AmcSubscriptionResponse amc = soldGold();
        ServiceOrderResponse order = redemption.redeem(org.getId(), amc.id(), PLAIN);

        assertEquals(carId, order.carId());
        assertEquals(ServiceOrderStatus.IN_PROGRESS, order.status());
        assertEquals(List.of("Oil change", "Car wash"), order.items().stream().map(i -> i.serviceName()).toList());
        order.items().forEach(i -> {
            assertMoney("100", i.discountPercent());
            assertMoney("0", i.lineGross());
        });
        assertMoney("0", order.netTotal());
        assertMoney("0", order.taxTotal());
        assertMoney("0", order.grossTotal());
        assertTrue(order.paid(), "a ₹0 AMC order is settled, never a bill");
        assertMoney("0", order.amountRemaining());
        assertEquals(amc.id(), order.amc().subscriptionId());
        assertEquals("Gold", order.amc().planName());
        assertEquals(1, order.amc().useNumber());
        assertEquals(12, order.amc().totalSlots());
        // the lines carry the AMC's own price / tax (not zeroed): 1000 @18% excl, 118 @18% incl.
        assertMoney("1000", order.items().get(0).basePrice());
        assertMoney("18", order.items().get(0).taxPercentage());
    }

    @Test
    void redeemingUsesTheCurrentPeriodAndTheCountsFollow() {
        AmcSubscriptionResponse amc = soldGold();
        redemption.redeem(org.getId(), amc.id(), PLAIN);

        AmcSubscriptionResponse after = read.get(org.getId(), amc.id());
        assertEquals(1, after.used());
        assertEquals(11, after.remaining());
        assertEquals(0, after.lapsed());
        assertEquals(0, after.availableNow());
    }

    @Test
    void thePeriodCannotBeUsedTwice() {
        AmcSubscriptionResponse amc = soldGold();
        redemption.redeem(org.getId(), amc.id(), PLAIN);
        assertEquals("amc_slot_used",
                assertThrows(ConflictException.class, () -> redemption.redeem(org.getId(), amc.id(), PLAIN)).getCode());
    }

    @Test
    void cancellingFreesTheSlotForANewRedemption() {
        AmcSubscriptionResponse amc = soldGold();
        ServiceOrderResponse first = redemption.redeem(org.getId(), amc.id(), PLAIN);
        orderUpdate.setStatus(org.getId(), first.id(), ServiceOrderStatus.CANCELLED);

        AmcSubscriptionResponse freed = read.get(org.getId(), amc.id());
        assertEquals(0, freed.used());
        assertEquals(1, freed.availableNow());

        ServiceOrderResponse second = redemption.redeem(org.getId(), amc.id(), PLAIN);
        assertEquals(ServiceOrderStatus.IN_PROGRESS, second.status());
        assertEquals(1, read.get(org.getId(), amc.id()).used());
        // and only one live order ever holds the slot
        assertEquals("amc_slot_used",
                assertThrows(ConflictException.class, () -> redemption.redeem(org.getId(), amc.id(), PLAIN)).getCode());
    }

    @Test
    void aCancelledAmcOrderCanNeverBeReopened() {
        AmcSubscriptionResponse amc = soldGold();
        ServiceOrderResponse order = redemption.redeem(org.getId(), amc.id(), PLAIN);
        orderUpdate.setStatus(org.getId(), order.id(), ServiceOrderStatus.CANCELLED);

        assertEquals("amc_order_reopen", assertThrows(ConflictException.class,
                () -> orderUpdate.setStatus(org.getId(), order.id(), ServiceOrderStatus.IN_PROGRESS)).getCode());
        assertEquals("amc_order_reopen", assertThrows(ConflictException.class, () -> orderUpdate.update(org.getId(), order.id(),
                new ServiceOrderRequest(null, null, null, null, null, ServiceOrderStatus.COMPLETED, null, null, null, null))).getCode());
    }

    @Test
    void deletingTheOrderFreesTheSlot() {
        AmcSubscriptionResponse amc = soldGold();
        ServiceOrderResponse order = redemption.redeem(org.getId(), amc.id(), PLAIN);
        orderDelete.delete(org.getId(), order.id());
        assertEquals(1, read.get(org.getId(), amc.id()).availableNow());
    }

    @Test
    void anAmcOrderCannotBeTurnedIntoABill() {
        AmcSubscriptionResponse amc = soldGold();
        ServiceOrderResponse order = redemption.redeem(org.getId(), amc.id(), PLAIN);

        // lines / discount cannot be sent...
        ServiceOrderRequest withLines = new ServiceOrderRequest(null, null, null, null, null, null, null, null, null,
                List.of(new ServiceOrderItemRequest(null, "Extra", new BigDecimal("500"), null, null, 1, null, null)));
        assertEquals("amc_order_locked",
                assertThrows(ConflictException.class, () -> orderUpdate.update(org.getId(), order.id(), withLines)).getCode());
        // ...and no payment can be recorded
        assertEquals("amc_order_locked", assertThrows(ConflictException.class,
                () -> addPayment.create(org.getId(), order.id(), new PaymentRequest(new BigDecimal("1"), PaymentType.CASH, null))).getCode());

        // the harmless fields still edit, and the lines stay at 100% off
        ServiceOrderResponse edited = orderUpdate.update(org.getId(), order.id(),
                new ServiceOrderRequest(null, null, employeeId, 1234, null, null, null, null, "note", null));
        assertEquals(employeeId, edited.employeeId());
        assertEquals(1234, edited.odometerReading());
        assertMoney("0", edited.grossTotal());
        assertEquals(2, edited.items().size());
        assertTrue(edited.paid());
    }

    @Test
    void expiredAndNotYetStartedAmcsCannotBeRedeemed() {
        AmcSubscriptionResponse amc = soldGold();
        startedMonthsAgo(amc.id(), 13);
        assertEquals("amc_expired",
                assertThrows(ConflictException.class, () -> redemption.redeem(org.getId(), amc.id(), PLAIN)).getCode());

        jdbc.update("UPDATE amc_subscriptions SET start_date = ? WHERE id = ?",
                LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(5), bytes(amc.id()));
        assertEquals("amc_not_started",
                assertThrows(ConflictException.class, () -> redemption.redeem(org.getId(), amc.id(), PLAIN)).getCode());
    }

    @Test
    void aSkippedPeriodLapsesAndOnlyTheCurrentOneIsRedeemable() {
        AmcSubscriptionResponse amc = soldGold();
        startedMonthsAgo(amc.id(), 3); // now in period 3 (0-based); periods 0..2 passed unused
        AmcSubscriptionResponse before = read.get(org.getId(), amc.id());
        assertEquals(3, before.lapsed());
        assertEquals(3, before.currentSlot());

        ServiceOrderResponse order = redemption.redeem(org.getId(), amc.id(), PLAIN);
        assertEquals(4, order.amc().useNumber());
        AmcSubscriptionResponse after = read.get(org.getId(), amc.id());
        assertEquals(1, after.used());
        assertEquals(3, after.lapsed());
        assertEquals(8, after.remaining()); // periods 4..11
    }

    @Test
    void anotherOrgCannotRedeemAndAPlanWithoutAmcIsBlocked() {
        AmcSubscriptionResponse amc = soldGold();
        as(otherOrg, UserRole.OWNER);
        assertThrows(NotFoundException.class, () -> redemption.redeem(otherOrg.getId(), amc.id(), PLAIN));
        as(noAmcOrg, UserRole.OWNER);
        assertEquals("amc_not_in_plan",
                assertThrows(ConflictException.class, () -> redemption.redeem(noAmcOrg.getId(), amc.id(), PLAIN)).getCode());
    }

    @Test
    void anyRoleCanRedeem() {
        AmcSubscriptionResponse amc = soldGold();
        as(org, UserRole.WORKER);
        assertTrue(redemption.redeem(org.getId(), amc.id(), PLAIN).paid());
    }

    @Test
    void redemptionLinesCarryTheRowQuantityStillAtFullDiscount() {
        AmcPlanResponse plan = planCreate.create(org.getId(), new AmcPlanRequest("Qty", List.of("Oil change", "Car wash")));
        UUID variantId = variantCreate.create(org.getId(), plan.id(), new AmcVariantRequest(12, 1, List.of(
                new AmcVariantRequest.Row("Oil change", 4, new BigDecimal("200"), new BigDecimal("18"), false),
                new AmcVariantRequest.Row("Car wash", 1, new BigDecimal("100"), null, false)))).variants().get(0).id();
        AmcSubscriptionResponse amc = sell.create(org.getId(),
                new AmcSaleRequest(carId, null, variantId, null, null, null, PaymentType.CASH, null));

        ServiceOrderResponse order = redemption.redeem(org.getId(), amc.id(), PLAIN);
        assertEquals(4, order.items().get(0).quantity());
        assertMoney("200", order.items().get(0).basePrice()); // unit price kept
        assertMoney("200", order.items().get(0).discountAmount()); // per unit, full price
        assertMoney("100", order.items().get(0).discountPercent());
        assertMoney("0", order.items().get(0).lineGross());
        assertMoney("0", order.grossTotal());
        assertTrue(order.paid());
    }
}
