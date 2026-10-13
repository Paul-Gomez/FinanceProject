package com.fincore.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "refresh_token_hash", nullable = false)
    private String refreshTokenHash;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    protected UserSession() {
    }

    public UserSession(UUID userId, String refreshTokenHash, String deviceName, String userAgent,
                       String ipAddress, Instant now, Instant expiresAt) {
        this.userId = userId;
        this.refreshTokenHash = refreshTokenHash;
        this.deviceName = deviceName;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.createdAt = now;
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(String reason, Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
            this.revokedReason = reason;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
