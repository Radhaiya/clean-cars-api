package com.example.cleancarsapi.repository.internal;

import com.example.cleancarsapi.entity.internal.AllowedEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AllowedEmailRepository extends JpaRepository<AllowedEmail, UUID> {

    Optional<AllowedEmail> findByEmail(String email);

    List<AllowedEmail> findByOrderByCreatedAtDesc();
}
