package com.example.cleancarsapi.dto;

import java.util.UUID;

/** A bike owned by a customer, with brand/model resolved to names (either may be null). */
public record CustomerBikeSummary(UUID id, String bikeNumber, String brand, String model) {
}
