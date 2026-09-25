package com.example.cleancarsapi.service.internal;

import com.example.cleancarsapi.dto.internal.InternalOrgDetailResponse;
import com.example.cleancarsapi.dto.internal.InternalOrgSummaryResponse;
import com.example.cleancarsapi.dto.internal.InternalOrgTotalsResponse;
import com.example.cleancarsapi.dto.internal.InternalPaymentResponse;
import com.example.cleancarsapi.dto.internal.InternalSubscriptionResponse;
import com.example.cleancarsapi.dto.internal.InternalUserResponse;
import com.example.cleancarsapi.dto.internal.ConsoleTimes;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import com.example.cleancarsapi.repository.EmployeeRepository;
import com.example.cleancarsapi.repository.ExpenseRepository;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.repository.RazorpayPaymentRepository;
import com.example.cleancarsapi.repository.ServiceCatalogRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import com.example.cleancarsapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only org views for the internal console — global, not org-scoped: an
 * unknown id is a plain 404 (admin sees everything, no tenant ambiguity).
 * Totals are all-time {@code WHERE org_id = ?} counts; payments join through
 * the local subscription.
 */
@Service
@RequiredArgsConstructor
public class OrgReadService {

    private static final int RECENT_PAYMENTS = 10;

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final SubscriptionReadService subscriptions;
    private final RazorpayPaymentRepository payments;
    private final CarRepository cars;
    private final BikeRepository bikes;
    private final CustomerRepository customers;
    private final EmployeeRepository employees;
    private final ServiceOrderRepository serviceOrders;
    private final ServiceCatalogRepository serviceCatalog;
    private final ExpenseRepository expenses;

    @Transactional(readOnly = true)
    public Page<InternalOrgSummaryResponse> list(String search, Pageable pageable) {
        String term = normalize(search);
        return organizations.searchAcrossOrgs(term, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public InternalOrgDetailResponse detail(UUID orgId) {
        Organization org = organizations.findById(orgId)
                .orElseThrow(() -> new NotFoundException("organization", orgId));
        return toDetail(org);
    }

    @Transactional(readOnly = true)
    public InternalOrgTotalsResponse stats(UUID orgId) {
        organizations.findById(orgId)
                .orElseThrow(() -> new NotFoundException("organization", orgId));
        return toTotals(orgId);
    }

    InternalOrgSummaryResponse toSummary(Organization org) {
        SubscriptionReadService.LiveSubscription live = subscriptions.liveForOrg(org.getId()).orElse(null);
        return new InternalOrgSummaryResponse(
                org.getId(),
                org.getName(),
                org.getTimezone(),
                org.getCurrencyCode(),
                org.getCurrencySymbol(),
                org.getContactPhone(),
                org.getContactEmail(),
                ConsoleTimes.inZone(org.getCreatedAt(), org.getTimezone()),
                users.countByOrgId(org.getId()),
                live == null
                        ? InternalOrgSummaryResponse.NO_SUBSCRIPTION
                        : live.subscription().getStatus().name(),
                live == null ? null : live.plan().getName());
    }

    InternalOrgDetailResponse toDetail(Organization org) {
        SubscriptionReadService.LiveSubscription live = subscriptions.liveForOrg(org.getId()).orElse(null);
        String status = live == null ? InternalOrgSummaryResponse.NO_SUBSCRIPTION : live.subscription().getStatus().name();
        return new InternalOrgDetailResponse(
                org.getId(),
                org.getName(),
                org.getTimezone(),
                org.getCurrencyCode(),
                org.getCurrencySymbol(),
                org.getContactPhone(),
                org.getContactEmail(),
                ConsoleTimes.inZone(org.getCreatedAt(), org.getTimezone()),
                users.countByOrgId(org.getId()),
                status,
                live == null ? null : InternalSubscriptionResponse.of(live.subscription(), live.plan()),
                users.findByOrgId(org.getId()).stream().map(user -> toUser(org, user)).toList(),
                toTotals(org.getId()),
                recentPayments(org));
    }

    private List<InternalPaymentResponse> recentPayments(Organization org) {
        return payments.searchAcrossOrgs(org.getId(), PageRequest.of(0, RECENT_PAYMENTS))
                .getContent().stream()
                .map(p -> InternalPaymentResponse.from(p, org.getTimezone()))
                .toList();
    }

    private InternalOrgTotalsResponse toTotals(UUID orgId) {
        return new InternalOrgTotalsResponse(
                cars.countByOrgId(orgId),
                bikes.countByOrgId(orgId),
                customers.countByOrgId(orgId),
                employees.countByOrgId(orgId),
                serviceOrders.countByOrgId(orgId),
                serviceCatalog.countByOrgId(orgId),
                expenses.countByOrgId(orgId));
    }

    private InternalUserResponse toUser(Organization org, User user) {
        return new InternalUserResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.isTrialUsed(),
                user.getStatus(),
                ConsoleTimes.inZone(user.getCreatedAt(), org.getTimezone()));
    }

    private static String normalize(String search) {
        return (search == null || search.isBlank()) ? null : search;
    }
}
