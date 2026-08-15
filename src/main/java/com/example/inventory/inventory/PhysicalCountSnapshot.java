package com.example.inventory.inventory;

import java.time.Instant;

public record PhysicalCountSnapshot(int expectedQuantity, Instant capturedAt) {
}
