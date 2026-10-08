package com.example.cleancarsapi.dto;

import java.math.BigDecimal;

/**
 * One bar of a breakdown chart: {@code name} (a catalog service / an AMC plan), how many
 * ({@code count}) and the net-of-tax money ({@code revenue}) for it in the requested range.
 */
public record ChartBreakdownRow(String name, long count, BigDecimal revenue) {
}
