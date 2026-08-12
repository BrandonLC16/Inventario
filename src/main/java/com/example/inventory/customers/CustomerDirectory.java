package com.example.inventory.customers;

import java.util.UUID;

public interface CustomerDirectory {

    void requireActiveCustomer(UUID customerId);
}
