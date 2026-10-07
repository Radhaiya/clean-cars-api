package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.OrgTimeZoneResolver;
import com.example.cleancarsapi.dto.InvoiceRequest;
import com.example.cleancarsapi.dto.InvoiceResponse;
import com.example.cleancarsapi.entity.Invoice;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.InvoiceRepository;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import com.example.cleancarsapi.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** The invoice sub-resource of a service order: read, create (once) and edit. */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final ServiceOrderRepository orders;
    private final OrganizationRepository organizations;
    private final OrgTimeZoneResolver orgTimezones;

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID orgId, UUID orderId) {
        return InvoiceResponse.of(find(orgId, orderId));
    }

    @Transactional
    public InvoiceResponse create(UUID orgId, UUID orderId, InvoiceRequest request) {
        orders.findByIdAndOrgId(orderId, orgId)
                .orElseThrow(() -> new NotFoundException("service order", orderId));
        // Serialises numbering (and a double-click's second create) per org.
        organizations.findByIdForUpdate(orgId).orElseThrow(() -> new NotFoundException("organization", orgId));
        if (invoices.findByServiceOrderIdAndOrgId(orderId, orgId).isPresent()) {
            throw ConflictException.invoiceExists();
        }

        Invoice invoice = new Invoice();
        invoice.setOrgId(orgId);
        invoice.setServiceOrderId(orderId);
        invoice.setInvoiceNumber(invoices.maxInvoiceNumber(orgId) + 1);
        invoice.setInvoiceDate(request.invoiceDate() != null ? request.invoiceDate() : orgTimezones.now().toLocalDate());
        invoice.setCreatedBy(AuthContext.require().userId());
        apply(invoice, request);
        return InvoiceResponse.of(invoices.save(invoice));
    }

    @Transactional
    public InvoiceResponse update(UUID orgId, UUID orderId, InvoiceRequest request) {
        Invoice invoice = find(orgId, orderId);
        if (request.invoiceDate() != null) {
            invoice.setInvoiceDate(request.invoiceDate());
        }
        apply(invoice, request);
        return InvoiceResponse.of(invoice);
    }

    private Invoice find(UUID orgId, UUID orderId) {
        return invoices.findByServiceOrderIdAndOrgId(orderId, orgId)
                .orElseThrow(() -> new NotFoundException("invoice for service order", orderId));
    }

    private static void apply(Invoice invoice, InvoiceRequest request) {
        invoice.setNextServiceDate(request.nextServiceDate());
        invoice.setNextServiceKm(request.nextServiceKm());
        String notes = request.notes() == null ? "" : request.notes().trim();
        invoice.setNotes(notes.isEmpty() ? null : notes);
    }
}
