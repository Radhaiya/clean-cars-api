package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.AmcVariantRequest;
import com.example.cleancarsapi.entity.AmcPlanItem;
import com.example.cleancarsapi.entity.AmcVariantRow;
import com.example.cleancarsapi.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared variant validation: tenure/interval shape, and one price row per plan service (no more, no fewer). */
@Component
public class AmcVariantRowFactory {

    /** Tenure must be a whole number of frequency periods, otherwise the variant is rejected. */
    public void requireValidShape(AmcVariantRequest request) {
        if (request.tenureMonths() % request.intervalMonths() != 0) {
            throw new BadRequestException("Tenure (" + request.tenureMonths() + " months) must be a multiple of the frequency ("
                    + request.intervalMonths() + " months)");
        }
    }

    public List<AmcVariantRow> build(UUID variantId, List<AmcPlanItem> planItems, List<AmcVariantRequest.Row> requested) {
        Map<String, AmcVariantRequest.Row> byName = new HashMap<>();
        for (AmcVariantRequest.Row row : requested) {
            if (byName.put(row.serviceName().trim().toLowerCase(), row) != null) {
                throw new BadRequestException("Duplicate row for service: " + row.serviceName());
            }
        }
        if (byName.size() != planItems.size()) {
            throw new BadRequestException("A variant needs exactly one price row for each of the plan's "
                    + planItems.size() + " services");
        }
        List<AmcVariantRow> out = new ArrayList<>();
        for (AmcPlanItem item : planItems) {
            AmcVariantRequest.Row row = byName.get(item.getServiceName().toLowerCase());
            if (row == null) {
                throw new BadRequestException("Missing price row for service: " + item.getServiceName());
            }
            AmcVariantRow entity = new AmcVariantRow();
            entity.setVariantId(variantId);
            entity.setPlanItemId(item.getId());
            entity.setQuantity(row.quantity() == null ? 1 : row.quantity());
            entity.setPrice(row.price());
            entity.setTaxPercentage(row.taxPercentage());
            entity.setTaxIncluded(row.taxIncluded());
            out.add(entity);
        }
        return out;
    }
}
