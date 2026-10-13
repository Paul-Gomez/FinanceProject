package com.fincore.categories.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {

    public static final String DEFAULT_COLOR = "#9E9E9E";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", updatable = false)
    private UUID ownerId;

    @Column(name = "parent_id", updatable = false)
    private UUID parentId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CategoryKind kind;

    @Column(nullable = false)
    private String color;

    private String icon;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Category() {
    }

    public Category(UUID ownerId, String name, CategoryKind kind, String color, String icon) {
        this(ownerId, null, name, kind, color, icon);
    }

    public Category(UUID ownerId, UUID parentId, String name, CategoryKind kind, String color, String icon) {
        this.ownerId = ownerId;
        this.parentId = parentId;
        this.name = name;
        this.kind = kind;
        this.color = color != null ? color : DEFAULT_COLOR;
        this.icon = icon;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String name, String color, String icon) {
        this.name = name;
        this.color = color != null ? color : DEFAULT_COLOR;
        this.icon = icon;
        this.updatedAt = Instant.now();
    }

    public boolean isRoot() {
        return parentId == null;
    }

    public boolean isSystem() {
        return ownerId == null;
    }

    public boolean isVisibleTo(UUID userId) {
        return isSystem() || ownerId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public CategoryKind getKind() {
        return kind;
    }

    public String getColor() {
        return color;
    }

    public String getIcon() {
        return icon;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
