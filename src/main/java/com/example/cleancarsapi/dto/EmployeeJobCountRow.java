package com.example.cleancarsapi.dto;

import java.util.UUID;

/**
 * Query projection — one employee's count of non-cancelled job cards in range,
 * for kpi-tiles' revenue-by-employee breakdown. Internal — not returned to
 * clients as-is.
 */
public record EmployeeJobCountRow(UUID employeeId, long jobCount) {
}
