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
import com.example.cleancarsapi.service.AmcPlanCreateService;
import com.example.cleancarsapi.service.AmcPlanDeleteService;
import com.example.cleancarsapi.dto.AmcPlanSalesResponse;
import com.example.cleancarsapi.service.AmcPlanReadService;
import com.example.cleancarsapi.service.AmcPlanSalesService;
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

/** Selling an AMC: snapshot, per-row tax pricing, vehicle / variant rules, runtime counts, delete-guard. Rules: docs/FEATURE-AMC.md. */
@SpringBootTest
@ActiveProfiles("test")
class AmcSaleTest {

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
    @Autowired AmcPlanSalesService planSales;

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

    @Test
    void saleIsPricedPerRowTimesSlotsAndRecordsThePayment() {
        UUID[] gold = goldPlan();
        AmcSubscriptionResponse s = sell.create(org.getId(), new AmcSaleRequest(carId, null, gold[1], null, null,
                employeeId, PaymentType.UPI, null));

        assertEquals("Gold", s.planName());
        assertEquals(carId, s.carId());
        assertMoney("13200", s.saleNet());   // (1000 + 100) x 12
        assertMoney("2376", s.saleTax());    // (180 + 18) x 12
        assertMoney("15576", s.saleGross()); // 1298 x 12
        assertMoney("1298", s.bundleGross());
        assertEquals(PaymentType.UPI, s.paymentType());
        assertEquals(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")), s.paymentDate());
        assertEquals("Seller", s.soldByName());
        assertEquals(List.of("Oil change", "Car wash"), s.rows().stream().map(r -> r.serviceName()).toList());
    }

    @Test
    void aFreshAmcHasEveryUseRemainingAndOneAvailableNow() {
        UUID[] gold = goldPlan();
        AmcSubscriptionResponse s = sell.create(org.getId(), saleOfCar(gold[1]));
        assertEquals(AmcSlots.Status.ACTIVE, s.status());
        assertEquals(12, s.total());
        assertEquals(0, s.used());
        assertEquals(0, s.lapsed());
        assertEquals(12, s.remaining());
        assertEquals(1, s.availableNow());
        assertEquals(0, s.currentSlot());
    }

    @Test
    void anAmcStartingLaterIsUpcomingWithNothingAvailable() {
        UUID[] gold = goldPlan();
        LocalDate later = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(10);
        AmcSubscriptionResponse s = sell.create(org.getId(),
                new AmcSaleRequest(carId, null, gold[1], later, null, null, PaymentType.CASH, null));
        assertEquals(AmcSlots.Status.UPCOMING, s.status());
        assertEquals(0, s.availableNow());
        assertEquals(later.plusMonths(12), s.endDate());
    }

    @Test
    void aSoldAmcIsASnapshotLaterTemplateEditsDoNotChangeIt() {
        UUID[] gold = goldPlan();
        AmcSubscriptionResponse s = sell.create(org.getId(), saleOfCar(gold[1]));

        variantUpdate.update(org.getId(), gold[0], gold[1], new AmcVariantRequest(12, 1, List.of(
                new AmcVariantRequest.Row("Oil change", null, new BigDecimal("1"), null, false),
                new AmcVariantRequest.Row("Car wash", null, new BigDecimal("1"), null, false))));

        AmcSubscriptionResponse after = read.get(org.getId(), s.id());
        assertMoney("15576", after.saleGross());
        assertMoney("1000", after.rows().get(0).price());
        assertMoney("18", after.rows().get(0).taxPercentage());
    }

    @Test
    void everythingOnARowIsEditableAtSale() {
        UUID[] gold = goldPlan();
        AmcSubscriptionResponse s = sell.create(org.getId(), new AmcSaleRequest(carId, null, gold[1], null,
                List.of(new AmcVariantRequest.Row("Oil change", null, new BigDecimal("500"), null, false),
                        new AmcVariantRequest.Row("Car wash", null, new BigDecimal("100"), new BigDecimal("10"), false)),
                null, PaymentType.CARD, null));
        // bundle: 500 + (100 + 10) = 610 ; x 12 = 7320
        assertMoney("7320", s.saleGross());
        assertMoney("7200", s.saleNet());
        assertMoney("120", s.saleTax());
    }

    @Test
    void sellingToABikeWorksAndEachVehicleListsItsOwnAmcs() {
        UUID[] gold = goldPlan();
        sell.create(org.getId(), saleOfCar(gold[1]));
        sell.create(org.getId(), saleOfCar(gold[1])); // a second AMC on the same car is allowed
        sell.create(org.getId(), new AmcSaleRequest(null, bikeId, gold[1], null, null, null, PaymentType.CASH, null));

        assertEquals(2, read.listForVehicle(org.getId(), carId, null).size());
        List<AmcSubscriptionResponse> bike = read.listForVehicle(org.getId(), null, bikeId);
        assertEquals(1, bike.size());
        assertEquals(bikeId, bike.get(0).bikeId());
    }

    @Test
    void aPastStartDateAndAmbiguousVehiclesAreRejected() {
        UUID[] gold = goldPlan();
        LocalDate yesterday = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).minusDays(1);
        assertThrows(BadRequestException.class, () -> sell.create(org.getId(),
                new AmcSaleRequest(carId, null, gold[1], yesterday, null, null, PaymentType.CASH, null)));
        assertThrows(BadRequestException.class, () -> sell.create(org.getId(),
                new AmcSaleRequest(carId, bikeId, gold[1], null, null, null, PaymentType.CASH, null)));
        assertThrows(BadRequestException.class, () -> sell.create(org.getId(),
                new AmcSaleRequest(null, null, gold[1], null, null, null, PaymentType.CASH, null)));
    }

    @Test
    void otherOrgsVehicleVariantAndSellerAreNotFound() {
        UUID[] gold = goldPlan();
        assertThrows(NotFoundException.class, () -> sell.create(org.getId(),
                new AmcSaleRequest(otherOrgCarId, null, gold[1], null, null, null, PaymentType.CASH, null)));
        assertThrows(NotFoundException.class, () -> sell.create(org.getId(),
                new AmcSaleRequest(carId, null, UUID.randomUUID(), null, null, null, PaymentType.CASH, null)));
        assertThrows(NotFoundException.class, () -> sell.create(org.getId(),
                new AmcSaleRequest(carId, null, gold[1], null, null, UUID.randomUUID(), PaymentType.CASH, null)));
        as(otherOrg, UserRole.OWNER);
        assertThrows(NotFoundException.class, () -> sell.create(otherOrg.getId(),
                new AmcSaleRequest(otherOrgCarId, null, gold[1], null, null, null, PaymentType.CASH, null)));
    }

    @Test
    void archivedPlansAndVariantsCannotBeSold() {
        UUID[] gold = goldPlan();
        variantUpdate.setArchived(org.getId(), gold[0], gold[1], true);
        assertEquals("amc_variant_archived",
                assertThrows(ConflictException.class, () -> sell.create(org.getId(), saleOfCar(gold[1]))).getCode());
        variantUpdate.setArchived(org.getId(), gold[0], gold[1], false);
        planUpdate.setArchived(org.getId(), gold[0], true);
        assertEquals("amc_variant_archived",
                assertThrows(ConflictException.class, () -> sell.create(org.getId(), saleOfCar(gold[1]))).getCode());
    }

    @Test
    void anyRoleCanSellButTheOrgsPlanMustIncludeAmc() {
        UUID[] gold = goldPlan();
        as(org, UserRole.WORKER);
        assertTrue(sell.create(org.getId(), saleOfCar(gold[1])).saleGross().signum() > 0);

        as(noAmcOrg, UserRole.OWNER);
        assertEquals("amc_not_in_plan", assertThrows(ConflictException.class,
                () -> sell.create(noAmcOrg.getId(), saleOfCar(gold[1]))).getCode());
    }

    @Test
    void soldPlansAndVariantsCanOnlyBeArchivedButUnsoldOnesCanBeDeleted() {
        UUID[] gold = goldPlan();
        sell.create(org.getId(), saleOfCar(gold[1]));

        assertEquals("amc_variant_sold", assertThrows(ConflictException.class,
                () -> planDelete.deleteVariant(org.getId(), gold[0], gold[1])).getCode());
        assertEquals("amc_plan_sold", assertThrows(ConflictException.class,
                () -> planDelete.deletePlan(org.getId(), gold[0])).getCode());

        AmcPlanResponse silver = planCreate.create(org.getId(), new AmcPlanRequest("Silver", List.of("Oil change", "Car wash")));
        UUID silverVariant = variantCreate.create(org.getId(), silver.id(), variant(12, 1)).variants().get(0).id();
        assertEquals(0, planDelete.deleteVariant(org.getId(), silver.id(), silverVariant).variants().size());
        planDelete.deletePlan(org.getId(), silver.id());
        assertThrows(NotFoundException.class, () -> planRead.get(org.getId(), silver.id()));
    }

    @Test
    void saleTotalFollowsRowQuantities() {
        UUID[] gold = goldPlan();
        AmcSubscriptionResponse s = sell.create(org.getId(), new AmcSaleRequest(carId, null, gold[1], null,
                List.of(new AmcVariantRequest.Row("Oil change", 3, new BigDecimal("100"), null, false),
                        new AmcVariantRequest.Row("Car wash", 1, new BigDecimal("50"), null, false)),
                null, PaymentType.CASH, null));
        // bundle: 300 + 50 = 350 ; x 12 = 4200
        assertMoney("350", s.bundleGross());
        assertMoney("4200", s.saleGross());
        assertEquals(3, s.rows().get(0).quantity());
        assertMoney("100", s.rows().get(0).price()); // per unit
        assertMoney("300", s.rows().get(0).gross()); // line
    }

    @Test
    void planSalesShowTotalsPerVariantAndOnlyTheSalesStillInForce() {
        AmcPlanResponse plan = planCreate.create(org.getId(), new AmcPlanRequest("Gold", List.of("Oil change", "Car wash")));
        UUID monthly = variantCreate.create(org.getId(), plan.id(), variant(12, 1)).variants().stream()
                .filter(v -> v.tenureMonths() == 12).findFirst().orElseThrow().id();
        UUID yearly = variantCreate.create(org.getId(), plan.id(), variant(24, 12)).variants().stream()
                .filter(v -> v.tenureMonths() == 24).findFirst().orElseThrow().id();
        LocalDate later = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(10);

        sell.create(org.getId(), new AmcSaleRequest(carId, null, monthly, null, null, null, PaymentType.CASH, null));
        sell.create(org.getId(), new AmcSaleRequest(null, bikeId, monthly, null, null, null, PaymentType.CASH, null));
        sell.create(org.getId(), new AmcSaleRequest(carId, null, yearly, later, null, null, PaymentType.CASH, null));
        AmcSubscriptionResponse old = sell.create(org.getId(),
                new AmcSaleRequest(carId, null, monthly, null, null, null, PaymentType.CASH, null));
        jdbc.update("UPDATE amc_subscriptions SET start_date = ? WHERE id = ?",
                LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).minusMonths(13), bytes(old.id()));

        AmcPlanSalesResponse r = planSales.get(org.getId(), plan.id());

        assertEquals(4, r.totals().totalSold());
        assertEquals(2, r.totals().active());
        assertEquals(1, r.totals().upcoming());
        assertEquals(1, r.totals().expired());
        // 3 monthly sales of 15576 + 1 yearly sale of 2 x 1298 = 46728 + 2596
        assertMoney("49324", r.totals().soldGross());

        AmcPlanSalesResponse.VariantSales m = r.variants().stream().filter(v -> v.variantId().equals(monthly)).findFirst().orElseThrow();
        assertEquals(3, m.counts().totalSold());
        assertEquals(2, m.counts().active());
        assertEquals(1, m.counts().expired());
        AmcPlanSalesResponse.VariantSales y = r.variants().stream().filter(v -> v.variantId().equals(yearly)).findFirst().orElseThrow();
        assertEquals(1, y.counts().totalSold());
        assertEquals(0, y.counts().active());
        assertEquals(1, y.counts().upcoming());

        // only active + upcoming are listed, active first, each with its vehicle, owner and runtime counts
        assertEquals(3, r.currentSales().size());
        assertEquals(AmcSlots.Status.ACTIVE, r.currentSales().get(0).amc().status());
        assertEquals(AmcSlots.Status.ACTIVE, r.currentSales().get(1).amc().status());
        assertEquals(AmcSlots.Status.UPCOMING, r.currentSales().get(2).amc().status());
        assertTrue(r.currentSales().stream().anyMatch(c -> c.vehicleKind().equals("CAR") && "S1".equals(c.vehicleNumber()) && "c".equals(c.ownerName())));
        assertTrue(r.currentSales().stream().anyMatch(c -> c.vehicleKind().equals("BIKE") && "B1".equals(c.vehicleNumber()) && "b".equals(c.ownerName())));
        assertEquals(1, r.currentSales().get(0).amc().availableNow());
    }

    @Test
    void planSalesAreOrgScopedAndNeedAmcInThePlan() {
        UUID[] gold = goldPlan();
        as(otherOrg, UserRole.OWNER);
        assertThrows(NotFoundException.class, () -> planSales.get(otherOrg.getId(), gold[0]));
        as(noAmcOrg, UserRole.OWNER);
        assertEquals("amc_not_in_plan", assertThrows(ConflictException.class, () -> planSales.get(noAmcOrg.getId(), gold[0])).getCode());
    }
}
