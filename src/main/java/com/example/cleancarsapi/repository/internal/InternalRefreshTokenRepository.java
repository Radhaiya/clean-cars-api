package com.example.cleancarsapi.repository.internal;

import com.example.cleancarsapi.entity.internal.InternalRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface InternalRefreshTokenRepository extends JpaRepository<InternalRefreshToken, UUID> {

    Optional<InternalRefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("""
            update InternalRefreshToken t
               set t.revokedAt = :now
             where t.email = :email
               and t.revokedAt is null
            """)
    int revokeAllForEmail(@Param("email") String email, @Param("now") LocalDateTime now);
}
