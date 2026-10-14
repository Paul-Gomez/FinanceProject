package com.fincore.categories.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category_rules")
public class CategoryRule {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false)
    private String pattern;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private int priority;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CategoryRule() {
    }

    /** El patrón tiene que llegar ya normalizado (ver TextNormalizer). */
    public CategoryRule(UUID ownerId, String normalizedPattern, UUID categoryId, int priority) {
        this.ownerId = ownerId;
        this.pattern = normalizedPattern;
        this.categoryId = categoryId;
        this.priority = priority;
        this.createdAt = Instant.now();
    }

    public boolean isSystem() {
        return ownerId == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getPattern() {
        return pattern;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public int getPriority() {
        return priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
