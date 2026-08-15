package com.example.inventory.inventory;

public enum StockMovementType {
    INITIAL_STOCK,
    MANUAL_IN,
    MANUAL_OUT,
    ORDER_RESERVED,
    ORDER_RESERVATION_RELEASED,
    ORDER_CONFIRMED,
    ORDER_CANCELLED,
    PURCHASE_RECEIVED
}
