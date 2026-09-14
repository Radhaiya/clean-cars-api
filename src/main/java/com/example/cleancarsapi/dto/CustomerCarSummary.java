package com.example.cleancarsapi.dto;

/** A car owned by a customer, with brand/model resolved to names (either may be null). */
public record CustomerCarSummary(Long id, String carNumber, String brand, String model) {
}
