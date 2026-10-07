package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.OrgTimeZoneResolver;
import com.example.cleancarsapi.dto.AmcInvoiceRequest;
import com.example.cleancarsapi.dto.AmcInvoiceResponse;
import com.example.cleancarsapi.entity.AmcInvoice;
import com.example.cleancarsapi.entity.AmcSubscription;
import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.AmcInvoiceRepository;
import com.example.cleancarsapi.repository.AmcSubscriptionRepository;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.internal.PlanLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** The invoice sub-resource of a sold AMC: read, create (once) and edit. Numbered {@code AMC-n} per org. */
@Service
@RequiredArgsConstructor
public class AmcInvoiceService {

    private final AmcInvoiceRepository invoices;
    private final AmcSubscriptionRepository subscriptions;
    private final OrganizationRepository organizations;
    private final PlanLimitService planLimits;
    private final OrgTimeZoneResolver orgTimezones;
    private final CarRepository cars;
    private final BikeRepository bikes;
    private final CustomerRepository customers;
    private final ServiceOrderAssembler vehicles;

    @Transactional(readOnly = true)
    public AmcInvoiceResponse get(UUID orgId, UUID subscriptionId) {
        planLimits.assertAmcEnabled(orgId);
        AmcSubscription sub = subscription(orgId, subscriptionId);
        return respond(orgId, sub, find(orgId, subscriptionId));
    }

    @Transactional
    public AmcInvoiceResponse create(UUID orgId, UUID subscriptionId, AmcInvoiceRequest request) {
        planLimits.assertAmcEnabled(orgId);
        AmcSubscription sub = subscription(orgId, subscriptionId);
        // Serialises numbering (and a double-click's second create) per org.
        organizations.findByIdForUpdate(orgId).orElseThrow(() -> new NotFoundException("organization", orgId));
        if (invoices.findByAmcSubscriptionIdAndOrgId(subscriptionId, orgId).isPresent()) {
            throw ConflictException.invoiceExists();
        }
        AmcInvoice invoice = new AmcInvoice();
        invoice.setOrgId(orgId);
        invoice.setAmcSubscriptionId(subscriptionId);
        invoice.setInvoiceNumber(invoices.maxInvoiceNumber(orgId) + 1);
        invoice.setInvoiceDate(request.invoiceDate() != null ? request.invoiceDate() : orgTimezones.now().toLocalDate());
        invoice.setCreatedBy(AuthContext.require().userId());
        invoice.setNotes(cleanNotes(request.notes()));
        return respond(orgId, sub, invoices.save(invoice));
    }

    @Transactional
    public AmcInvoiceResponse update(UUID orgId, UUID subscriptionId, AmcInvoiceRequest request) {
        planLimits.assertAmcEnabled(orgId);
        AmcSubscription sub = subscription(orgId, subscriptionId);
        AmcInvoice invoice = find(orgId, subscriptionId);
        if (request.invoiceDate() != null) {
            invoice.setInvoiceDate(request.invoiceDate());
        }
        invoice.setNotes(cleanNotes(request.notes()));
        return respond(orgId, sub, invoice);
    }

    private AmcSubscription subscription(UUID orgId, UUID id) {
        return subscriptions.findByIdAndOrgId(id, orgId).orElseThrow(() -> new NotFoundException("amc", id));
    }

    private AmcInvoice find(UUID orgId, UUID subscriptionId) {
        return invoices.findByAmcSubscriptionIdAndOrgId(subscriptionId, orgId)
                .orElseThrow(() -> new NotFoundException("invoice for amc", subscriptionId));
    }

    /** Bill-to = the vehicle's owner; resolves even when the vehicle or owner is soft-deleted. */
    private AmcInvoiceResponse respond(UUID orgId, AmcSubscription sub, AmcInvoice invoice) {
        Car car = sub.getCarId() == null ? null : cars.findByIdAndOrgId(sub.getCarId(), orgId).orElse(null);
        Bike bike = sub.getBikeId() == null ? null : bikes.findByIdAndOrgId(sub.getBikeId(), orgId).orElse(null);
        UUID customerId = car != null ? car.getCustomerId() : bike != null ? bike.getCustomerId() : null;
        Customer customer = customerId == null ? null : customers.findByIdAndOrgId(customerId, orgId).orElse(null);
        return AmcInvoiceResponse.of(invoice, customer == null ? null : customer.getName(),
                customer == null ? null : customer.getPhone(), vehicles.vehicleOf(orgId, car, bike));
    }

    private static String cleanNotes(String notes) {
        String trimmed = notes == null ? "" : notes.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
