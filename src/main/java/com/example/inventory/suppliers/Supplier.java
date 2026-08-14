package com.example.inventory.suppliers;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
class Supplier {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "legal_name", nullable = false, length = 160)
    private String legalName;

    @Column(name = "commercial_name", length = 160)
    private String commercialName;

    @Column(name = "fiscal_identifier", length = 32)
    private String fiscalIdentifier;

    @Column(length = 254)
    private String email;

    @Column(length = 32)
    private String phone;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Supplier() {
    }

    Supplier(String code, String legalName, String commercialName,
             String fiscalIdentifier, String email, String phone, boolean active) {
        this.id = UUID.randomUUID();
        update(code, legalName, commercialName, fiscalIdentifier, email, phone, active);
    }

    void update(String code, String legalName, String commercialName,
                String fiscalIdentifier, String email, String phone, boolean active) {
        this.code = code;
        this.legalName = legalName;
        this.commercialName = commercialName;
        this.fiscalIdentifier = fiscalIdentifier;
        this.email = email;
        this.phone = phone;
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
    String getCode() { return code; }
    String getLegalName() { return legalName; }
    String getCommercialName() { return commercialName; }
    String getFiscalIdentifier() { return fiscalIdentifier; }
    String getEmail() { return email; }
    String getPhone() { return phone; }
    boolean isActive() { return active; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
