package com.example.inventory.warehouses;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "warehouses")
class Warehouse {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 32) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Column(length = 1000) private String description;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Warehouse() { }

    Warehouse(String code, String name, String description, boolean active) {
        this.id = UUID.randomUUID();
        update(code, name, description, active);
    }

    void update(String code, String name, String description, boolean active) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.active = active;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    UUID getId() { return id; }
    String getCode() { return code; }
    String getName() { return name; }
    String getDescription() { return description; }
    boolean isActive() { return active; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
