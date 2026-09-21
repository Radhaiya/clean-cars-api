package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ServiceOrderItemRequest;
import com.example.cleancarsapi.entity.ServiceCatalog;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ServiceCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
/**
 * Builds {@link ServiceOrderItem} snapshots from the request lines. A supplied
 * {@code serviceCatalogId} is read once here to seed name / price / GST; any value
 * the caller passed wins over the seed. Nothing links the line back to the catalog.
 */
@Component
@RequiredArgsConstructor
public class ServiceOrderItemFactory {

    private final ServiceCatalogRepository catalog;

    public List<ServiceOrderItem> build(UUID orgId, UUID serviceOrderId, List<ServiceOrderItemRequest> requests) {
        List<ServiceOrderItem> out = new ArrayList<>();
        for (ServiceOrderItemRequest r : requests) {
            ServiceCatalog seed = null;
            if (r.serviceCatalogId() != null) {
                seed = catalog.findByIdAndOrgId(r.serviceCatalogId(), orgId)
                        .orElseThrow(() -> new NotFoundException("service", r.serviceCatalogId()));
            }

            String name = firstNonBlank(r.serviceName(), seed == null ? null : seed.getName());
            BigDecimal basePrice = r.basePrice() != null ? r.basePrice()
                    : (seed == null ? null : seed.getPrice());
            BigDecimal gstPercentage = r.gstPercentage() != null ? r.gstPercentage()
                    : (seed == null ? null : seed.getGstPercentage());
            boolean gstIncluded = r.gstIncluded() != null ? r.gstIncluded()
                    : (seed != null && seed.isGstIncluded());

            if (name == null || name.isBlank()) {
                throw new BadRequestException("serviceName is required for a line without a serviceCatalogId");
            }
            if (basePrice == null) {
                throw new BadRequestException("basePrice is required for a line without a serviceCatalogId");
            }

            ServiceOrderItem item = new ServiceOrderItem();
            item.setServiceOrderId(serviceOrderId);
            item.setServiceName(name.trim());
            item.setBasePrice(basePrice);
            item.setGstPercentage(gstPercentage);
            item.setGstIncluded(gstIncluded);
            item.setQuantity(r.quantity() == null || r.quantity() < 1 ? 1 : r.quantity());
            item.setNotes(r.notes());
            out.add(item);
        }
        return out;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
