package com.example.inventory.orders;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "orders")
class SalesOrder {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    @Column(nullable = false, unique = true, length = 32)
    private String folio;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal total;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by", length = 255)
    private String confirmedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", length = 255)
    private String cancelledBy;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productId ASC")
    @BatchSize(size = 100)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SalesOrder() {
    }

    SalesOrder(String folio, UUID customerId, String currency, String createdBy,
               List<PricedOrderItem> requestedItems) {
        this.id = UUID.randomUUID();
        this.status = OrderStatus.PENDING;
        this.folio = folio;
        this.customerId = customerId;
        this.currency = currency;
        this.createdBy = createdBy;
        replaceItems(requestedItems);
    }

    void replaceItems(List<PricedOrderItem> requestedItems) {
        items.clear();
        requestedItems.forEach(item -> items.add(
                new OrderItem(this, item.productId(), item.quantity(), item.unitPrice())));
        total = items.stream().map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    void confirm(String actor) {
        status = OrderStatus.CONFIRMED;
        confirmedAt = Instant.now();
        confirmedBy = actor;
    }

    void cancel(String actor) {
        status = OrderStatus.CANCELLED;
        cancelledAt = Instant.now();
        cancelledBy = actor;
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
    String getFolio() { return folio; }
    UUID getCustomerId() { return customerId; }
    OrderStatus getStatus() { return status; }
    String getCurrency() { return currency; }
    BigDecimal getTotal() { return total; }
    String getCreatedBy() { return createdBy; }
    Instant getConfirmedAt() { return confirmedAt; }
    String getConfirmedBy() { return confirmedBy; }
    Instant getCancelledAt() { return cancelledAt; }
    String getCancelledBy() { return cancelledBy; }
    List<OrderItem> getItems() { return List.copyOf(items); }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
