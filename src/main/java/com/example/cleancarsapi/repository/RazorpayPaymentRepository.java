package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.RazorpayPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RazorpayPaymentRepository extends JpaRepository<RazorpayPayment, UUID> {

    Optional<RazorpayPayment> findByRazorpayPaymentId(String razorpayPaymentId);
}
