package com.example.inventory.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

interface StockMovementRepository extends JpaRepository<StockMovement, UUID>,
        JpaSpecificationExecutor<StockMovement> {
}
