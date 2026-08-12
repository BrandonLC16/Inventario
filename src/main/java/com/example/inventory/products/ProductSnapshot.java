package com.example.inventory.products;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSnapshot(UUID id, BigDecimal price) {
}
