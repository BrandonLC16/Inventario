package com.example.inventory.customers;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
class Customer {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "fiscal_identifier", length = 32)
    private String fiscalIdentifier;

    @Column(length = 254)
    private String email;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Customer() {
    }

    Customer(String name, String fiscalIdentifier, String email, boolean active) {
        this.id = UUID.randomUUID();
        update(name, fiscalIdentifier, email, active);
    }

    void update(String name, String fiscalIdentifier, String email, boolean active) {
        this.name = name;
        this.fiscalIdentifier = fiscalIdentifier;
        this.email = email;
        this.active = active;
    }

    void deactivate() {
        active = false;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    UUID getId() { return id; }
    String getName() { return name; }
    String getFiscalIdentifier() { return fiscalIdentifier; }
    String getEmail() { return email; }
    boolean isActive() { return active; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
