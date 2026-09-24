package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    /** Case-insensitive email lookup (invite addressing normalizes to lowercase). */
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByFirebaseUid(String firebaseUid);

    long countByOrgId(UUID orgId);

    /** The org's member accounts (for employee-list joins — employees are the seat). */
    List<User> findByOrgId(UUID orgId);

    /** Row-locked fetch — serialises concurrent trial-starts on the {@code trial_used} latch. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
