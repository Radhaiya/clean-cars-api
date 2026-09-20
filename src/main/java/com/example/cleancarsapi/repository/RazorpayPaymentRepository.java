package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.RazorpayPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RazorpayPaymentRepository extends JpaRepository<RazorpayPayment, Long> {

    Optional<RazorpayPayment> findByRazorpayPaymentId(String razorpayPaymentId);
}
