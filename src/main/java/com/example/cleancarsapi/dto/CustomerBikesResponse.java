package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Customer;

import java.util.List;
import java.util.UUID;
/**
 * One customer with their bikes, for the "pick a customer, get their bike(s)"
 * flow when opening a service order. {@code bikes} carries the {@code bikeId} +
 * bike number (plus brand/model names) the caller needs for the order payload.
 */
public record CustomerBikesResponse(
        UUID customerId,
        String customerName,
        String phone,
        List<CustomerBikeSummary> bikes
) {
    public static CustomerBikesResponse of(Customer customer, List<CustomerBikeSummary> bikes) {
        return new CustomerBikesResponse(customer.getId(), customer.getName(), customer.getPhone(), bikes);
    }
}
