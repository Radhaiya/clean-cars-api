package com.example.cleancarsapi;

import com.example.cleancarsapi.dto.AmcPlanRenameRequest;
import com.example.cleancarsapi.dto.AmcPlanRequest;
import com.example.cleancarsapi.dto.AmcPlanResponse;
import com.example.cleancarsapi.dto.AmcVariantRequest;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.ForbiddenException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.repository.SubscriptionRepository;
import com.example.cleancarsapi.security.AuthenticatedUser;
import com.example.cleancarsapi.service.AmcPlanCreateService;
import com.example.cleancarsapi.service.AmcPlanReadService;
import com.example.cleancarsapi.service.AmcPlanUpdateService;
import com.example.cleancarsapi.service.AmcVariantCreateService;
import com.example.cleancarsapi.service.AmcVariantUpdateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AMC plans + variants: per-row tax totals, tenure/frequency shape, roles, plan gating, archive. Rules: docs/FEATURE-AMC.md. */
@SpringBootTest
@ActiveProfiles("test")
class AmcPlanTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired OrganizationRepository organizations;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired AmcPlanCreateService createService;
    @Autowired AmcPlanReadService readService;
    @Autowired AmcPlanUpdateService updateService;
    @Autowired AmcVariantCreateService variantCreate;
    @Autowired AmcVariantUpdateService variantUpdate;

    UUID planWithAmc;
    UUID planWithoutAmc;
    Organization org;
    Organization noAmcOrg;
    Organization otherOrg;
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

    @BeforeEach
    void setup() {
        planWithAmc = UUID.randomUUID();
        planWithoutAmc = UUID.randomUUID();
        jdbc.update("INSERT INTO subscription_plans (id, name, is_trial, max_users, invoice_generation, is_public, sort_order) "
                + "VALUES (?, 'AmcOn', FALSE, 5, FALSE, FALSE, 990)", bytes(planWithAmc));
        jdbc.update("INSERT INTO subscription_plans (id, name, is_trial, max_users, invoice_generation, amc_enabled, is_public, sort_order) "
                + "VALUES (?, 'AmcOff', FALSE, 5, FALSE, FALSE, FALSE, 991)", bytes(planWithoutAmc));
        org = orgOn(planWithAmc, "amc-org");
        otherOrg = orgOn(planWithAmc, "amc-other-org");
        noAmcOrg = orgOn(planWithoutAmc, "amc-off-org");
        as(org, UserRole.OWNER);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        for (UUID id : orgIds) {
            byte[] o = bytes(id);
            jdbc.update("DELETE FROM amc_variant_rows WHERE variant_id IN (SELECT id FROM amc_plan_variants WHERE org_id = ?)", o);
            jdbc.update("DELETE FROM amc_plan_variants WHERE org_id = ?", o);
            jdbc.update("DELETE FROM amc_plan_items WHERE plan_id IN (SELECT id FROM amc_plans WHERE org_id = ?)", o);
            jdbc.update("DELETE FROM amc_plans WHERE org_id = ?", o);
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

    private AmcPlanResponse gold() {
        return createService.create(org.getId(), new AmcPlanRequest("Gold", List.of("Oil change", "Car wash")));
    }

    private static AmcVariantRequest variant(int tenure, int interval) {
        return new AmcVariantRequest(tenure, interval, List.of(
                new AmcVariantRequest.Row("Oil change", null, new BigDecimal("1000"), new BigDecimal("18"), false),
                new AmcVariantRequest.Row("car wash", null, new BigDecimal("118"), new BigDecimal("18"), true)));
    }

    @Test
    void totalsAreBundleTimesSlotsWithTaxPerRow() {
        AmcPlanResponse plan = gold();
        AmcPlanResponse withVariant = variantCreate.create(org.getId(), plan.id(), variant(12, 1));

        AmcPlanResponse.Variant v = withVariant.variants().get(0);
        assertEquals(12, v.totalSlots());
        assertEquals(List.of("Oil change", "Car wash"), withVariant.serviceNames());
        // row 1: 1000 net + 180 tax = 1180 ; row 2: 118 incl. -> net 100, tax 18 ; bundle gross 1298
        assertMoney("1100", v.bundleNet());
        assertMoney("198", v.bundleTax());
        assertMoney("1298", v.bundleGross());
        assertMoney("15576", v.totalGross()); // 1298 x 12 slots
    }

    @Test
    void yearlyFrequencyOverTwoYearsIsTwoSlots() {
        AmcPlanResponse plan = gold();
        AmcPlanResponse.Variant v = variantCreate.create(org.getId(), plan.id(), variant(24, 12)).variants().get(0);
        assertEquals(2, v.totalSlots());
    }

    @Test
    void tenureMustBeAMultipleOfTheFrequency() {
        AmcPlanResponse plan = gold();
        assertThrows(BadRequestException.class, () -> variantCreate.create(org.getId(), plan.id(), variant(10, 3)));
    }

    @Test
    void rowsMustMatchThePlanServicesExactly() {
        AmcPlanResponse plan = gold();
        AmcVariantRequest missing = new AmcVariantRequest(12, 1, List.of(
                new AmcVariantRequest.Row("Oil change", null, BigDecimal.TEN, null, false)));
        assertThrows(BadRequestException.class, () -> variantCreate.create(org.getId(), plan.id(), missing));
        AmcVariantRequest unknown = new AmcVariantRequest(12, 1, List.of(
                new AmcVariantRequest.Row("Oil change", null, BigDecimal.TEN, null, false),
                new AmcVariantRequest.Row("Tyre rotation", null, BigDecimal.TEN, null, false)));
        assertThrows(BadRequestException.class, () -> variantCreate.create(org.getId(), plan.id(), unknown));
    }

    @Test
    void duplicateServiceNamesAndPlanNamesAreRejected() {
        assertThrows(BadRequestException.class,
                () -> createService.create(org.getId(), new AmcPlanRequest("Dup", List.of("Wash", "wash"))));
        gold();
        ConflictException e = assertThrows(ConflictException.class, this::gold);
        assertEquals("amc_plan_name_exists", e.getCode());
    }

    @Test
    void sameTenureAndFrequencyCannotBeAddedTwice() {
        AmcPlanResponse plan = gold();
        variantCreate.create(org.getId(), plan.id(), variant(12, 1));
        ConflictException e = assertThrows(ConflictException.class,
                () -> variantCreate.create(org.getId(), plan.id(), variant(12, 1)));
        assertEquals("amc_variant_exists", e.getCode());
        // a different shape of the same plan is fine
        assertEquals(2, variantCreate.create(org.getId(), plan.id(), variant(24, 1)).variants().size());
    }

    @Test
    void variantRowsAreEditableButServiceNamesStayFixed() {
        AmcPlanResponse plan = gold();
        UUID variantId = variantCreate.create(org.getId(), plan.id(), variant(12, 1)).variants().get(0).id();
        AmcVariantRequest cheaper = new AmcVariantRequest(12, 1, List.of(
                new AmcVariantRequest.Row("Oil change", null, new BigDecimal("800"), null, false),
                new AmcVariantRequest.Row("Car wash", null, new BigDecimal("100"), null, false)));
        AmcPlanResponse.Variant v = variantUpdate.update(org.getId(), plan.id(), variantId, cheaper).variants().get(0);
        assertMoney("900", v.bundleGross());
        assertMoney("10800", v.totalGross());
        assertEquals(List.of("Oil change", "Car wash"), readService.get(org.getId(), plan.id()).serviceNames());
    }

    @Test
    void onlyOwnerOrManagerEditsButAnyoneReads() {
        AmcPlanResponse plan = gold();
        as(org, UserRole.WORKER);
        assertThrows(ForbiddenException.class, this::gold);
        assertThrows(ForbiddenException.class,
                () -> updateService.rename(org.getId(), plan.id(), new AmcPlanRenameRequest("X")));
        assertEquals(plan.id(), readService.get(org.getId(), plan.id()).id());
        as(org, UserRole.MANAGER);
        assertEquals("Silver", updateService.rename(org.getId(), plan.id(), new AmcPlanRenameRequest("Silver")).name());
    }

    @Test
    void orgsOnAPlanWithoutAmcAreBlocked() {
        as(noAmcOrg, UserRole.OWNER);
        ConflictException write = assertThrows(ConflictException.class,
                () -> createService.create(noAmcOrg.getId(), new AmcPlanRequest("Gold", List.of("Wash"))));
        assertEquals("amc_not_in_plan", write.getCode());
        ConflictException read = assertThrows(ConflictException.class,
                () -> readService.list(noAmcOrg.getId(), null, false, PageRequest.of(0, 20)));
        assertEquals("amc_not_in_plan", read.getCode());
    }

    @Test
    void archivedPlansAreHiddenByDefaultAndTakeNoNewVariants() {
        AmcPlanResponse plan = gold();
        assertTrue(updateService.setArchived(org.getId(), plan.id(), true).archived());
        assertEquals(0, readService.list(org.getId(), null, false, PageRequest.of(0, 20)).totalElements());
        assertEquals(1, readService.list(org.getId(), null, true, PageRequest.of(0, 20)).totalElements());
        ConflictException e = assertThrows(ConflictException.class,
                () -> variantCreate.create(org.getId(), plan.id(), variant(12, 1)));
        assertEquals("amc_plan_archived", e.getCode());
        assertFalse(updateService.setArchived(org.getId(), plan.id(), false).archived());
    }

    @Test
    void archivingAVariantKeepsItListedButFlagged() {
        AmcPlanResponse plan = gold();
        UUID variantId = variantCreate.create(org.getId(), plan.id(), variant(12, 1)).variants().get(0).id();
        AmcPlanResponse after = variantUpdate.setArchived(org.getId(), plan.id(), variantId, true);
        assertTrue(after.variants().get(0).archived());
    }

    @Test
    void anotherOrgCannotSeeOrTouchAPlan() {
        AmcPlanResponse plan = gold();
        as(otherOrg, UserRole.OWNER);
        assertThrows(NotFoundException.class, () -> readService.get(otherOrg.getId(), plan.id()));
        assertThrows(NotFoundException.class,
                () -> updateService.rename(otherOrg.getId(), plan.id(), new AmcPlanRenameRequest("Hijack")));
        assertEquals(0, readService.list(otherOrg.getId(), null, true, PageRequest.of(0, 20)).totalElements());
    }

    @Test
    void rowQuantityMultipliesTheRowAndTheBundle() {
        AmcPlanResponse plan = gold();
        AmcVariantRequest request = new AmcVariantRequest(12, 1, List.of(
                new AmcVariantRequest.Row("Oil change", 2, new BigDecimal("1000"), new BigDecimal("18"), false),
                new AmcVariantRequest.Row("Car wash", null, new BigDecimal("118"), new BigDecimal("18"), true)));
        AmcPlanResponse.Variant v = variantCreate.create(org.getId(), plan.id(), request).variants().get(0);

        assertEquals(2, v.rows().get(0).quantity());
        assertEquals(1, v.rows().get(1).quantity()); // null = 1
        assertMoney("2360", v.rows().get(0).gross()); // (1000 + 180) x 2
        assertMoney("1000", v.rows().get(0).price().multiply(BigDecimal.ONE)); // price stays per unit
        assertMoney("2478", v.bundleGross());         // 2360 + 118
        assertMoney("29736", v.totalGross());         // x 12 uses
    }

    @Test
    void aZeroOrNegativeQuantityIsRejectedByValidation() {
        var violations = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator().validate(
                new AmcVariantRequest.Row("Oil change", 0, BigDecimal.TEN, null, false));
        assertFalse(violations.isEmpty());
    }
}
