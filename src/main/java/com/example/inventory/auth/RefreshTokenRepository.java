package com.example.inventory.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Query("select token.user.id from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<UUID> findUserIdByTokenHash(@Param("tokenHash") byte[] tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") byte[] tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken token
               set token.revokedAt = :revokedAt
             where token.familyId = :familyId
               and token.revokedAt is null
            """)
    int revokeActiveFamily(@Param("familyId") UUID familyId,
                           @Param("revokedAt") Instant revokedAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken token
               set token.revokedAt = :revokedAt
             where token.user.id = :userId
               and token.revokedAt is null
            """)
    int revokeAllActiveByUserId(@Param("userId") UUID userId,
                                @Param("revokedAt") Instant revokedAt);
}
