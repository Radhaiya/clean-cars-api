package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.EventProcessingStatus;
import com.example.cleancarsapi.entity.PaymentEvent;
import com.example.cleancarsapi.entity.RazorpayPayment;
import com.example.cleancarsapi.entity.RazorpayPaymentStatus;
import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.exception.RazorpayWebhookException;
import com.example.cleancarsapi.repository.PaymentEventRepository;
import com.example.cleancarsapi.repository.RazorpayPaymentRepository;
import com.example.cleancarsapi.repository.SubscriptionPlanRepository;
import com.example.cleancarsapi.repository.SubscriptionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;

/**
 * Razorpay webhook processing. The controller verifies the HMAC signature; the
 * {@code payment_events.razorpay_event_id} unique constraint dedupes (Razorpay
 * delivers at-least-once — a PROCESSED/IGNORED duplicate returns 200 without
 * reprocessing, a FAILED one is retried by Razorpay and updated in place); the raw
 * payload is appended; then the status matrix is applied to the local
 * {@code subscriptions} row and actual charges are snapshotted into {@code razorpay_payments}.
 *
 * <p>A webhook referencing a subscription we don't know yet (the event can fire
 * before the subscribe-transaction commits) is recorded FAILED and rethrown →
 * non-200 → Razorpay retries later. {@code noRollbackFor} keeps that FAILED row
 * committed even though we rethrow (same pattern as the token-reuse lockout).
 * Every step is idempotent, so retries and out-of-order deliveries converge
 * instead of corrupting state.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RazorpayWebhookService {

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    /** A duplicate delivery of one of these states is already conclusively handled. */
    private static final Set<EventProcessingStatus> CONCLUSIVELY_HANDLED =
            EnumSet.of(EventProcessingStatus.PROCESSED, EventProcessingStatus.IGNORED);

    private final PaymentEventRepository paymentEvents;
    private final RazorpayPaymentRepository razorpayPayments;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository subscriptionPlans;
    private final ObjectMapper objectMapper;

    /**
     * Handle one webhook delivery. Throws on failures the event row could not ride
     * through — the controller translates a throw into a non-200 so Razorpay retries.
     */
    @Transactional(noRollbackFor = RazorpayWebhookException.class)
    public void handle(String eventId, String eventType, String rawBody) {
        if (paymentEvents.existsByRazorpayEventIdAndProcessingStatusIn(eventId, CONCLUSIVELY_HANDLED)) {
            log.info("Webhook {} ({}) already handled — duplicate delivery ignored", eventId, eventType);
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (tools.jackson.core.JacksonException e) {
            // Jackson 3 parse failures are unchecked JacksonException (invalid JSON body)
            throw new RazorpayWebhookException("Unparseable webhook payload for event " + eventId);
        }
        JsonNode subEntity = root.path("payload").path("subscription").path("entity");
        JsonNode payEntity = root.path("payload").path("payment").path("entity");
        String rzpSubId = text(subEntity, "id");
        String rzpPaymentId = text(payEntity, "id");

        PaymentEvent event = recordEvent(eventId, eventType, rzpSubId, rzpPaymentId, rawBody);

        try {
            switch (eventType != null ? eventType : "") {
                case "subscription.activated", "subscription.resumed" -> activate(rzpSubId, subEntity);
                case "subscription.charged" -> {
                    Subscription sub = activate(rzpSubId, subEntity);
                    upsertPayment(eventType, rzpPaymentId, payEntity, sub.getId());
                }
                case "subscription.updated" -> {
                    // Plan change (up/downgrade via our API or the Dashboard). Razorpay did the
                    // proration already (differential charge/refund); all we sync is which local
                    // plan + cycle the subscription now points at. No status change per Razorpay.
                    Subscription sub = requireLocalSubscription(rzpSubId);
                    String newPlanId = text(subEntity, "plan_id");
                    if (newPlanId != null) {
                        applyPlanMapping(sub, newPlanId);
                    }
                }
                case "payment.authorized", "payment.captured", "payment.failed" -> {
                    // A payment event by itself makes no access decision — the subscription
                    // lifecycle event does. Only capture the snapshot when placeable.
                    if (rzpSubId != null) {
                        Subscription sub = requireLocalSubscription(rzpSubId);
                        upsertPayment(eventType, rzpPaymentId, payEntity, sub.getId());
                    }
                }
                case "subscription.pending" -> requireLocalSubscription(rzpSubId).setStatus(SubscriptionStatus.PAST_DUE);
                case "subscription.halted" -> requireLocalSubscription(rzpSubId).setStatus(SubscriptionStatus.HALTED);
                case "subscription.cancelled" -> requireLocalSubscription(rzpSubId).setStatus(SubscriptionStatus.CANCELLED);
                case "subscription.completed", "subscription.expiry" ->
                        requireLocalSubscription(rzpSubId).setStatus(SubscriptionStatus.EXPIRED);
                default -> {
                    event.setProcessingStatus(EventProcessingStatus.IGNORED);
                    event.setProcessedAt(now());
                    paymentEvents.save(event);
                    log.info("Webhook event {} of type {} ignored", eventId, eventType);
                    return;
                }
            }

            event.setProcessingStatus(EventProcessingStatus.PROCESSED);
            event.setProcessedAt(now());
            paymentEvents.save(event);
            log.info("Webhook {} ({}) processed -> {}", eventId, eventType, rzpSubId);

        } catch (RuntimeException e) {
            // FAILED row stays committed (noRollbackFor on the exception below) + rethrow →
            // non-200 → Razorpay retries → the FAILED row is found and reprocessed.
            event.setProcessingStatus(EventProcessingStatus.FAILED);
            event.setErrorMessage(e.getMessage());
            event.setProcessedAt(now());
            paymentEvents.save(event);
            log.error("Webhook {} ({}) FAILED: {}", eventId, eventType, e.getMessage());
            throw e instanceof RazorpayWebhookException webhookException
                    ? webhookException
                    : new RazorpayWebhookException("Webhook " + eventId + " processing failed: " + e.getMessage());
        }
    }

    private Subscription requireLocalSubscription(String razorpaySubscriptionId) {
        return subscriptions.findByRazorpaySubscriptionId(razorpaySubscriptionId)
                .orElseThrow(() -> new RazorpayWebhookException(
                        "No local subscription for Razorpay subscription " + razorpaySubscriptionId
                        + " — event arrived before the local row exists / references a foreign subscription"));
    }

    /** On activation, mark ACTIVE and supersede a still-live trial row (trial ends early — it was free). */
    private Subscription activate(String rzpSubId, JsonNode subEntity) {
        Subscription sub = requireLocalSubscription(rzpSubId);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        applyPeriod(sub, subEntity);
        syncPlanMapping(sub, subEntity);
        // Snapshot the autopay method (card / upi / …) — changePlan needs it to reject
        // UPI mandates up-front (Razorpay PATCHes them with a 400).
        sub.setPaymentMethod(text(subEntity, "payment_method"));
        supersedeLiveTrial(sub);
        log.info("Razorpay subscription {} activated (org {})", rzpSubId, sub.getOrgId());
        return sub;
    }

    /**
     * Keep the local plan/cycle in step with whichever Razorpay plan the event body
     * names — covers plan changes arriving with the next charge after a cycle-end
     * schedule, too. Unknown ids (types not in our catalogue) leave the row alone.
     */
    private void syncPlanMapping(Subscription sub, JsonNode subEntity) {
        String planId = text(subEntity, "plan_id");
        if (planId != null) {
            applyPlanMapping(sub, planId);
        }
    }

    private void applyPlanMapping(Subscription sub, String razorpayPlanId) {
        subscriptionPlans.findByRazorpayMonthlyPlanIdOrRazorpayYearlyPlanId(razorpayPlanId, razorpayPlanId)
                .ifPresent(plan -> {
                    com.example.cleancarsapi.entity.BillingCycle cycle =
                            razorpayPlanId.equals(plan.getRazorpayMonthlyPlanId())
                                    ? com.example.cleancarsapi.entity.BillingCycle.MONTHLY
                                    : com.example.cleancarsapi.entity.BillingCycle.YEARLY;
                    if (!plan.getId().equals(sub.getPlanId()) || sub.getBillingCycle() != cycle) {
                        sub.setPlanId(plan.getId());
                        sub.setBillingCycle(cycle);
                        log.info("Subscription row {} now maps to plan {} ({} via Razorpay plan {})",
                                sub.getId(), plan.getName(), cycle, razorpayPlanId);
                    }
                });
    }

    private void supersedeLiveTrial(Subscription activated) {
        subscriptions.findFirstByOrgIdAndStatusInOrderByCreatedAtDesc(
                        activated.getOrgId(), EnumSet.of(SubscriptionStatus.TRIALING))
                .filter(trial -> !trial.getId().equals(activated.getId()))
                .ifPresent(trial -> {
                    trial.setStatus(SubscriptionStatus.CANCELLED);
                    trial.setEndDate(now().toLocalDate());
                    log.info("Trial row {} superseded by activated Razorpay subscription {} (org {})",
                            trial.getId(), activated.getRazorpaySubscriptionId(), activated.getOrgId());
                });
    }

    /** Razorpay's current_start/current_end (epoch seconds) → the local period; nulls left alone. */
    private void applyPeriod(Subscription sub, JsonNode razorpaySubscription) {
        Long start = epoch(razorpaySubscription, "current_start");
        Long end = epoch(razorpaySubscription, "current_end");
        if (start != null) {
            sub.setStartDate(Instant.ofEpochSecond(start).atZone(UTC).toLocalDate());
        }
        if (end != null) {
            sub.setEndDate(Instant.ofEpochSecond(end).atZone(UTC).toLocalDate());
        }
    }

    /** Upsert the charge snapshot keyed by the Razorpay payment id (idempotent across duplicate webhooks). */
    private void upsertPayment(String eventType, String rzpPaymentId, JsonNode payEntity, Long localSubId) {
        if (rzpPaymentId == null || payEntity.isMissingNode() || payEntity.isNull()) {
            return;
        }
        RazorpayPayment payment = razorpayPayments.findByRazorpayPaymentId(rzpPaymentId)
                .orElseGet(() -> {
                    RazorpayPayment fresh = new RazorpayPayment();
                    fresh.setRazorpayPaymentId(rzpPaymentId);
                    fresh.setSubscriptionId(localSubId);
                    return fresh;
                });
        payment.setSubscriptionId(localSubId);
        payment.setRazorpayOrderId(text(payEntity, "order_id"));
        payment.setRazorpayInvoiceId(text(payEntity, "invoice_id"));
        payment.setAmount(payEntity.path("amount").asLong(0));
        payment.setCurrency(text(payEntity, "currency"));
        payment.setStatus(paymentStatus(eventType, payEntity));
        Long paidAt = epoch(payEntity, "captured_at");
        if (paidAt == null) {
            paidAt = epoch(payEntity, "created_at");
        }
        payment.setPaidAt(paidAt == null
                ? null : LocalDateTime.ofInstant(Instant.ofEpochSecond(paidAt), UTC));
        razorpayPayments.save(payment);
    }

    /** The event type decides direction; the entity's own status field is the fallback. */
    private static RazorpayPaymentStatus paymentStatus(String eventType, JsonNode payEntity) {
        return switch (eventType) {
            case "payment.captured" -> RazorpayPaymentStatus.CAPTURED;
            case "payment.failed" -> RazorpayPaymentStatus.FAILED;
            case "payment.authorized" -> RazorpayPaymentStatus.AUTHORIZED;
            default -> switch (payEntity.path("status").asText("")) {
                case "captured" -> RazorpayPaymentStatus.CAPTURED;
                case "authorized" -> RazorpayPaymentStatus.AUTHORIZED;
                case "failed" -> RazorpayPaymentStatus.FAILED;
                case "refunded" -> RazorpayPaymentStatus.REFUNDED;
                default -> RazorpayPaymentStatus.CREATED;
            };
        };
    }

    private PaymentEvent recordEvent(
            String eventId, String eventType, String rzpSubId, String rzpPaymentId, String rawBody) {
        PaymentEvent event = new PaymentEvent();
        event.setRazorpayEventId(eventId);
        event.setEventType(eventType);
        event.setRazorpaySubscriptionId(rzpSubId);
        event.setRazorpayPaymentId(rzpPaymentId);
        event.setProcessingStatus(EventProcessingStatus.RECEIVED);
        event.setPayloadJson(rawBody);
        try {
            // flush: a concurrent delivery of the same event that passed its own exists-check
            // surfaces here (same unique key) — signal "already being handled", answer 200.
            return paymentEvents.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            PaymentEvent existing = paymentEvents.findByRazorpayEventId(eventId)
                    .filter(row -> row.getProcessingStatus() == EventProcessingStatus.FAILED)
                    .orElseThrow(() -> new DuplicateWebhookException(eventId));
            // Razorpay retrying a FAILED event: re-run on the same row, not a new insert.
            existing.setEventType(eventType);
            existing.setRazorpaySubscriptionId(rzpSubId);
            existing.setRazorpayPaymentId(rzpPaymentId);
            existing.setPayloadJson(rawBody);
            existing.setProcessingStatus(EventProcessingStatus.RECEIVED);
            existing.setErrorMessage(null);
            existing.setProcessedAt(null);
            paymentEvents.save(existing);
            return existing;
        }
    }

    // ------- payload field readers -------

    private static String text(JsonNode node, String field) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static Long epoch(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull() || !v.canConvertToLong())
                ? null : Long.valueOf(v.asLong());
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(UTC);
    }
}
