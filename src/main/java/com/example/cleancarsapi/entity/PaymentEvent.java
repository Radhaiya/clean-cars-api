package com.example.cleancarsapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code payment_events} row — append-only audit of every meaningful Razorpay
 * webhook. {@code razorpayEventId} is the idempotency key: duplicate deliveries
 * (Razorpay retries at-least-once) are detected by the unique constraint and
 * answered 200 without reprocessing.
 */
@Entity
@Table(name = "payment_events")
@Getter
@Setter
@NoArgsConstructor
public class PaymentEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "razorpay_event_id", nullable = false, unique = true)
    private String razorpayEventId;

    @Column(nullable = false)
    private String eventType;

    private String razorpaySubscriptionId;

    private String razorpayPaymentId;

    @Column(nullable = false)
    private EventProcessingStatus processingStatus;

    /** Raw webhook body (the full JSON payload). */
    @Column(nullable = false)
    private String payloadJson;

    private String errorMessage;

    @Column(insertable = false, updatable = false)
    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;
}
