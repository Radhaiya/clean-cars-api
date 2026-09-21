package com.example.cleancarsapi.dto;

import java.util.UUID;

/** A car owned by a customer, with brand/model resolved to names (either may be null). */
public record CustomerCarSummary(UUID id, String carNumber, String brand, String model) {
}
