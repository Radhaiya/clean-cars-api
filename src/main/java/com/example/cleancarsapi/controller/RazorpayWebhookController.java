package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.service.DuplicateWebhookException;
import com.example.cleancarsapi.service.RazorpayGateway;
import com.example.cleancarsapi.service.RazorpayWebhookService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;


@RestController
@RequestMapping("/api/webhooks/razorpay")
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookController {

    private final RazorpayGateway razorpay;
    private final RazorpayWebhookService webhookService;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId,
            @RequestHeader(value = "X-Razorpay-Event-Type", required = false) String headerEventType,
            @RequestBody(required = false) String rawBody) {

        if (!razorpay.verifyWebhookSignature(rawBody, signature)) {
            log.warn("Razorpay webhook rejected: missing/invalid X-Razorpay-Signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String type = headerEventType;
        String id = eventId;
        if (!StringUtils.hasText(type)) {
            type = parseEventType(rawBody);
        }
        if (!StringUtils.hasText(id)) {
            id = sha256Hex(rawBody == null ? "" : rawBody);
        }

        try {
            webhookService.handle(id, type, rawBody);
        } catch (DuplicateWebhookException e) {
            // Concurrent delivery of an event already being handled — answer 200.
            log.info("Razorpay webhook {}: concurrently handled duplicate", id);
        }
        return ResponseEntity.ok().build();
    }

    /** The payload's top-level {@code event} field — Razorpay's own event type name. */
    private static String parseEventType(String rawBody) {
        try {
            JsonNode root = new ObjectMapper().readTree(rawBody);
            return root.path("event").asText(null);
        } catch (Exception e) {
            return null; // unparseable → dedupe key still exists; handle() fails the body as well
        }
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
