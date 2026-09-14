package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for a customer. {@code orgId} is never accepted here — it comes from the token. */
public record CustomerRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String phone,
        @Size(max = 255) String altPhone,
        @Email @Size(max = 255) String email,
        @Size(max = 255) String address,
        String notes
) {
    /** Copy the mutable fields onto an entity (shared by the create and update services). */
    public void applyTo(Customer customer) {
        customer.setName(name);
        customer.setPhone(phone);
        customer.setAltPhone(altPhone);
        customer.setEmail(email);
        customer.setAddress(address);
        customer.setNotes(notes);
    }
}
