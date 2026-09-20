package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.RazorpayProperties;
import com.example.cleancarsapi.exception.RazorpayApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * Thin Razorpay REST client (basic-auth over HTTPS — no vendor SDK, the API surface
 * this codebase touches is three endpoints). Razorpay amounts are raw paise; the
 * {@code amount()} helpers convert to rupee decimals for the JSON boundary.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(RazorpayProperties.class)
public class RazorpayGateway {

    /** Razorpay raw amount (paise) per unit of rupees. */
    private static final BigDecimal PAISE_PER_RUPEE = BigDecimal.valueOf(100);
    private static final String API_BASE = "https://api.razorpay.com/v1";

    /**
     * Razorpay requires a positive {@code total_count} (there is no -1/unlimited value) —
     * a large fixed cycle count stands in for "bill until the customer cancels" for both
     * monthly and yearly plans (100 cycles is ~8 years monthly, ~100 years yearly).
     */
    private static final int TOTAL_COUNT_CYCLES = 100;

    private final RazorpayProperties props;

    /** Public key id, handed to the frontend for Razorpay Checkout. */
    public String keyId() {
        return props.keyId();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RazorpayPlan(String id, Item item) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Item(String name, Long amount, String currency, String period, Integer interval) {
        }

        /** Price in rupee-precision rupees (Razorpay stores paise). */
        public BigDecimal amountRupees() {
            return BigDecimal.valueOf(item.amount()).divide(PAISE_PER_RUPEE);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RazorpaySubscriptionCreated(String id, String status,
                                              @JsonProperty("plan_id") String planId) {
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl(API_BASE)
                .defaultHeaders(headers -> headers.setBasicAuth(props.keyId(), props.keySecret()))
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    /**
     * Change the plan on a live Razorpay subscription. Razorpay does the proration:
     * an immediate upgrade charges only the remaining amount (differential invoice on
     * the existing autopay), a downgrade refunds the difference; the authoritative
     * result arrives via the {@code subscription.updated} webhook.
     */
    public void updateSubscription(String razorpaySubscriptionId, String newRazorpayPlanId) {
        try {
            client().patch()
                    .uri("/subscriptions/{id}", razorpaySubscriptionId)
                    .body(Map.of(
                            "plan_id", newRazorpayPlanId,
                            "schedule_change_at", "now",
                            "customer_notify", true))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new RazorpayApiException(
                    "Razorpay plan change failed for subscription " + razorpaySubscriptionId
                    + " -> " + newRazorpayPlanId, e);
        }
    }

    /** GET /v1/plans/{id} — the live price/currency/cycle of one Razorpay plan. */
    public RazorpayPlan fetchPlan(String razorpayPlanId) {
        try {
            RazorpayPlan plan = client().get()
                    .uri("/plans/{id}", razorpayPlanId)
                    .retrieve()
                    .body(RazorpayPlan.class);
            if (plan == null) {
                throw new RazorpayApiException("Razorpay returned an empty response for plan " + razorpayPlanId);
            }
            return plan;
        } catch (RestClientException e) {
            throw new RazorpayApiException("Razorpay plan fetch failed for " + razorpayPlanId, e);
        }
    }

    /**
     * Create a Razorpay subscription: infinite billing cycles against the chosen plan.
     * Notes carry our local ids so webhook events can be traced back to this org.
     */
    public RazorpaySubscriptionCreated createSubscription(String razorpayPlanId, long orgId, String userEmail) {
        try {
            RazorpaySubscriptionCreated created = client().post()
                    .uri("/subscriptions")
                    .body(Map.of(
                            "plan_id", razorpayPlanId,
                            "total_count", TOTAL_COUNT_CYCLES,
                            "notes", Map.of(
                                    "org_id", String.valueOf(orgId),
                                    "user_email", String.valueOf(userEmail))))
                    .retrieve()
                    .body(RazorpaySubscriptionCreated.class);
            if (created == null || !StringUtils.hasText(created.id())) {
                throw new RazorpayApiException("Razorpay returned no subscription id for plan " + razorpayPlanId);
            }
            return created;
        } catch (RestClientException e) {
            throw new RazorpayApiException("Razorpay subscription creation failed for plan " + razorpayPlanId, e);
        }
    }

    /** HMAC-SHA256(raw body, webhook secret) matches the X-Razorpay-Signature hex digest? */
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        if (!StringUtils.hasText(signature) || !StringUtils.hasText(props.webhookSecret())) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            return java.security.MessageDigest.isEqual(
                    HexFormat.of().parseHex(signature), digest);
        } catch (Exception e) {
            log.warn("Webhook signature verification error: {}", e.getMessage());
            return false;
        }
    }
}
