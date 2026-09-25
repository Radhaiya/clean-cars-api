package com.example.cleancarsapi.dto.internal;

import com.example.cleancarsapi.entity.PaymentEvent;

import java.util.UUID;

/**
 * One {@code payment_events} audit row (subscription lifecycle webhooks). The
 * raw payload stays out of listings — big and rarely needed.
 */
public record InternalPaymentEventResponse(
        UUID id,
        String razorpayEventId,
        String eventType,
        String processingStatus,
        String receivedAt,
        String processedAt,
        String errorMessage
) {
    public static InternalPaymentEventResponse from(PaymentEvent e, String timezone) {
        return new InternalPaymentEventResponse(
                e.getId(),
                e.getRazorpayEventId(),
                e.getEventType(),
                e.getProcessingStatus().name(),
                ConsoleTimes.inZone(e.getReceivedAt(), timezone),
                ConsoleTimes.inZone(e.getProcessedAt(), timezone),
                e.getErrorMessage());
    }
}
