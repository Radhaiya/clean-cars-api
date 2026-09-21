package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Customer;

import java.util.List;
import java.util.UUID;
/**
 * One customer with their cars, for the "pick a customer, get their car(s)" flow
 * when opening a service order. {@code cars} carries the {@code carId} + car number
 * (plus brand/model names) the caller needs for the order payload.
 */
public record CustomerCarsResponse(
        UUID customerId,
        String customerName,
        String phone,
        List<CustomerCarSummary> cars
) {
    public static CustomerCarsResponse of(Customer customer, List<CustomerCarSummary> cars) {
        return new CustomerCarsResponse(customer.getId(), customer.getName(), customer.getPhone(), cars);
    }
}
