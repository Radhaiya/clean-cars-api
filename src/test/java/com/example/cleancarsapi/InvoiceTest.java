package com.example.cleancarsapi;

import com.example.cleancarsapi.dto.InvoiceRequest;
import com.example.cleancarsapi.dto.InvoiceResponse;
import com.example.cleancarsapi.dto.ServiceOrderItemRequest;
import com.example.cleancarsapi.dto.ServiceOrderRequest;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.entity.PaymentPlan;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.security.AuthenticatedUser;
import com.example.cleancarsapi.dto.OrganizationInvoiceSettingsRequest;
import com.example.cleancarsapi.entity.InvoiceTemplate;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.exception.ForbiddenException;
import com.example.cleancarsapi.service.InvoiceService;
import com.example.cleancarsapi.service.OrganizationService;
import com.example.cleancarsapi.service.ServiceOrderCreateService;
import com.example.cleancarsapi.service.ServiceOrderDeleteService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Invoices: per-org numbering, one per order, edit, and removal with the order. */
@SpringBootTest
@ActiveProfiles("test-api")
class InvoiceTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired ServiceOrderCreateService createService;
    @Autowired ServiceOrderDeleteService deleteService;
    @Autowired InvoiceService invoices;
    @Autowired OrganizationService organizationService;

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
        jdbc.update("INSERT INTO organizations (id, name) VALUES (?, 'invoice-test')", bytes(orgId));
        jdbc.update("INSERT INTO users (id, name) VALUES (?, 'invoice-test')", bytes(userId));
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
        jdbc.update("DELETE FROM invoices WHERE org_id = ?", org);
        jdbc.update("DELETE FROM payments WHERE org_id = ?", org);
        jdbc.update("DELETE FROM service_order_items WHERE service_order_id IN (SELECT id FROM service_orders WHERE org_id = ?)", org);
        jdbc.update("DELETE FROM service_orders WHERE org_id = ?", org);
        jdbc.update("DELETE FROM cars WHERE org_id = ?", org);
        jdbc.update("DELETE FROM customers WHERE org_id = ?", org);
        jdbc.update("DELETE FROM users WHERE id = ?", bytes(userId));
        jdbc.update("DELETE FROM organizations WHERE id = ?", org);
    }

    private ServiceOrderResponse order() {
        return createService.create(orgId, new ServiceOrderRequest(carId, null, null, null, null, null, PaymentPlan.ONE_TIME, null, null,
                List.of(new ServiceOrderItemRequest(null, "svc", new BigDecimal("100"), null, null, 1, null, null))));
    }

    @Test
    void numbersRunPerOrgAndEachOrderGetsOneInvoice() {
        ServiceOrderResponse a = order();
        ServiceOrderResponse b = order();
        LocalDate next = LocalDate.of(2026, 2, 9);

        InvoiceResponse first = invoices.create(orgId, a.id(), new InvoiceRequest(null, next, 75835, "  thanks  "));
        assertEquals(1, first.invoiceNumber());
        assertEquals(next, first.nextServiceDate());
        assertEquals(75835, first.nextServiceKm());
        assertEquals("thanks", first.notes());
        assertEquals(2, invoices.create(orgId, b.id(), new InvoiceRequest(null, null, null, null)).invoiceNumber());

        ConflictException again = assertThrows(ConflictException.class,
                () -> invoices.create(orgId, a.id(), new InvoiceRequest(null, null, null, null)));
        assertEquals("invoice_exists", again.getCode());
    }

    @Test
    void updateKeepsNumberAndDateWhenOmittedAndClearsTheHints() {
        ServiceOrderResponse o = order();
        InvoiceResponse created = invoices.create(orgId, o.id(),
                new InvoiceRequest(LocalDate.of(2025, 11, 9), LocalDate.of(2026, 2, 9), 100, "x"));

        InvoiceResponse updated = invoices.update(orgId, o.id(), new InvoiceRequest(null, null, null, null));
        assertEquals(created.invoiceNumber(), updated.invoiceNumber());
        assertEquals(LocalDate.of(2025, 11, 9), updated.invoiceDate());
        assertNull(updated.nextServiceDate());
        assertNull(updated.notes());
    }

    @Test
    void missingInvoiceIsNotFoundAndDeletingTheOrderRemovesIt() {
        ServiceOrderResponse o = order();
        assertThrows(NotFoundException.class, () -> invoices.get(orgId, o.id()));

        invoices.create(orgId, o.id(), new InvoiceRequest(null, null, null, null));
        deleteService.delete(orgId, o.id());
        assertThrows(NotFoundException.class, () -> invoices.get(orgId, o.id()));
    }

    @Test
    void onlyTheOwnerSetsTheInvoiceTemplateAndColour() {
        OrganizationInvoiceSettingsRequest request = new OrganizationInvoiceSettingsRequest(InvoiceTemplate.BOLD, "#1a2b3c");
        assertThrows(ForbiddenException.class, () -> organizationService.updateInvoiceSettings(orgId, request));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, orgId, UserRole.OWNER, "t@t", "t"), null, List.of()));
        Organization saved = organizationService.updateInvoiceSettings(orgId, request);
        assertEquals(InvoiceTemplate.BOLD, saved.getInvoiceTemplate());
        assertEquals("#1A2B3C", saved.getInvoiceColor());
    }

    @Test
    void numbersAreSequentialInEachOrgIndependently() {
        UUID org2 = UUID.randomUUID();
        UUID customer2 = UUID.randomUUID();
        UUID car2 = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, name) VALUES (?, 'invoice-test-2')", bytes(org2));
        jdbc.update("INSERT INTO customers (id, org_id, name, phone) VALUES (?, ?, 'c2', '2')", bytes(customer2), bytes(org2));
        jdbc.update("INSERT INTO cars (id, org_id, customer_id, car_number) VALUES (?, ?, ?, 'T2')", bytes(car2), bytes(org2), bytes(customer2));
        try {
            InvoiceRequest empty = new InvoiceRequest(null, null, null, null);
            ServiceOrderRequest second = new ServiceOrderRequest(car2, null, null, null, null, null, PaymentPlan.ONE_TIME, null, null,
                    List.of(new ServiceOrderItemRequest(null, "svc", new BigDecimal("100"), null, null, 1, null, null)));
            // interleave the two orgs: each must still count 1, 2, 3 on its own
            assertEquals(1, invoices.create(orgId, order().id(), empty).invoiceNumber());
            assertEquals(1, invoices.create(org2, createService.create(org2, second).id(), empty).invoiceNumber());
            assertEquals(2, invoices.create(orgId, order().id(), empty).invoiceNumber());
            assertEquals(2, invoices.create(org2, createService.create(org2, second).id(), empty).invoiceNumber());
            assertEquals(3, invoices.create(orgId, order().id(), empty).invoiceNumber());
        } finally {
            byte[] o2 = bytes(org2);
            jdbc.update("DELETE FROM invoices WHERE org_id = ?", o2);
            jdbc.update("DELETE FROM service_order_items WHERE service_order_id IN (SELECT id FROM service_orders WHERE org_id = ?)", o2);
            jdbc.update("DELETE FROM service_orders WHERE org_id = ?", o2);
            jdbc.update("DELETE FROM cars WHERE org_id = ?", o2);
            jdbc.update("DELETE FROM customers WHERE org_id = ?", o2);
            jdbc.update("DELETE FROM organizations WHERE id = ?", o2);
        }
    }
}
