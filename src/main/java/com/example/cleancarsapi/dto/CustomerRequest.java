package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.Pattern;
import com.example.cleancarsapi.entity.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for a customer. {@code orgId} is never accepted here — it comes from the token. */
public record CustomerRequest(
        @NotBlank @Size(min = FieldLimits.NAME_MIN, max = FieldLimits.NAME_MAX, message = FieldLimits.NAME_MSG) String name,
        @NotBlank @Size(min = FieldLimits.PHONE_MIN, max = FieldLimits.PHONE_MAX, message = FieldLimits.PHONE_MSG) @Pattern(regexp = FieldLimits.PHONE_RE, message = FieldLimits.PHONE_MSG) String phone,
        @Size(max = FieldLimits.PHONE_MAX, message = FieldLimits.PHONE_MSG) @Pattern(regexp = FieldLimits.PHONE_RE, message = FieldLimits.PHONE_MSG) String altPhone,
        @Email @Size(max = FieldLimits.EMAIL_MAX) String email,
        @Size(max = FieldLimits.ADDRESS_MAX) String address,
        @Size(max = FieldLimits.NOTES_MAX) String notes
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
