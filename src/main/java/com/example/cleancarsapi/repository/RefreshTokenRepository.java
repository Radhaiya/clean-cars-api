package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("""
            update RefreshToken r
               set r.revokedAt = :now
             where r.userId = :userId
               and r.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") long userId, @Param("now") LocalDateTime now);
}
