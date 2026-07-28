package com.example.inventory.auth;

import com.example.inventory.users.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
class RefreshToken {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "bytea")
    private byte[] tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by", unique = true)
    private RefreshToken replacedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    RefreshToken(UserAccount user, UUID familyId, byte[] tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.familyId = familyId;
        this.tokenHash = Arrays.copyOf(tokenHash, tokenHash.length);
        this.expiresAt = expiresAt;
    }

    void revoke(Instant revokedAt, RefreshToken replacement) {
        this.revokedAt = revokedAt;
        this.replacedBy = replacement;
    }

    boolean isExpiredAt(Instant instant) {
        return !expiresAt.isAfter(instant);
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    UUID getId() {
        return id;
    }

    UserAccount getUser() {
        return user;
    }

    UUID getFamilyId() {
        return familyId;
    }

    byte[] getTokenHash() {
        return Arrays.copyOf(tokenHash, tokenHash.length);
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    RefreshToken getReplacedBy() {
        return replacedBy;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
