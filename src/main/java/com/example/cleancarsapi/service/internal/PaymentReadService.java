package com.example.cleancarsapi.service.internal;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.internal.InternalPaymentEventResponse;
import com.example.cleancarsapi.dto.internal.InternalPaymentResponse;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.repository.PaymentEventRepository;
import com.example.cleancarsapi.repository.RazorpayPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only Razorpay money views for the internal console — straight from the
 * tables the main API maintains (no live Razorpay fetch), amounts converted
 * from paise to rupee decimals per row; timestamps rendered in the org's zone
 * when the request is filtered to one (a cross-org unfiltered page has no
 * single org zone — it renders UTC).
 */
@Service
@RequiredArgsConstructor
public class PaymentReadService {

    private final RazorpayPaymentRepository payments;
    private final PaymentEventRepository events;
    private final OrganizationRepository organizations;

    @Transactional(readOnly = true)
    public PageResponse<InternalPaymentResponse> search(UUID orgId, Pageable pageable) {
        String timezone = timezoneOf(orgId);
        return PageResponse.of(payments.searchAcrossOrgs(orgId, pageable)
                .map(p -> InternalPaymentResponse.from(p, timezone)));
    }

    @Transactional(readOnly = true)
    public PageResponse<InternalPaymentEventResponse> events(UUID orgId, Pageable pageable) {
        String timezone = timezoneOf(orgId);
        return PageResponse.of(events.searchAcrossOrgs(orgId, pageable)
                .map(e -> InternalPaymentEventResponse.from(e, timezone)));
    }

    /** Rows render in the filtered org's zone; UTC for an unfiltered page. */
    private String timezoneOf(UUID orgId) {
        return orgId == null ? null
                : organizations.findById(orgId).map(o -> o.getTimezone()).orElse(null);
    }
}
