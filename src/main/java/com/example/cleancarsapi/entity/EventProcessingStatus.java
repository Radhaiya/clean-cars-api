package com.example.cleancarsapi.entity;

/** Processing state of a stored Razorpay webhook event, persisted lowercase via {@link EventProcessingStatusConverter}. */
public enum EventProcessingStatus {
    RECEIVED,
    PROCESSED,
    IGNORED,
    FAILED;

    public static EventProcessingStatus fromDb(String value) {
        return EventProcessingStatus.valueOf(value.trim().toUpperCase());
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
