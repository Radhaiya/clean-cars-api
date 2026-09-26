package com.example.cleancarsapi.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A vehicle's past service orders plus the header stats the detail endpoints
 * derive from them. Cancelled orders are excluded from every aggregate.
 */
public record VehicleHistory(
        List<CarServiceSummary> services,
        long totalServices,
        BigDecimal totalRevenue,
        Integer lastOdometerReading
) {
    public static VehicleHistory empty() {
        return new VehicleHistory(List.of(), 0L, BigDecimal.ZERO, null);
    }

    public static VehicleHistory of(List<CarServiceSummary> services,
                                    long totalServices,
                                    BigDecimal totalRevenue,
                                    Integer lastOdometerReading) {
        return new VehicleHistory(services, totalServices, totalRevenue, lastOdometerReading);
    }
}
