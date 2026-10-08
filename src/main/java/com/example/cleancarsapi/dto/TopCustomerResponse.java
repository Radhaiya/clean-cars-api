package com.example.cleancarsapi.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row of the "highest revenue customers" list — lifetime, no date range, net of tax.
 * {@code serviceRevenue}: money received on their service orders; {@code amcRevenue}: AMCs sold to the cars and
 * bikes they currently own (an AMC follows its vehicle); {@code totalRevenue} is their sum. {@code jobs}:
 * non-cancelled service orders; {@code amcsBought}: AMCs sold to their vehicles.
 */
public record TopCustomerResponse(
        UUID customerId,
        String name,
        String phone,
        long jobs,
        long amcsBought,
        BigDecimal serviceRevenue,
        BigDecimal amcRevenue,
        BigDecimal totalRevenue
) {
}
