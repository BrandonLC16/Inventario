package com.example.inventory.customers;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

interface CustomerRepository extends JpaRepository<Customer, UUID>,
        JpaSpecificationExecutor<Customer> {

    boolean existsByFiscalIdentifierAndIdNot(String fiscalIdentifier, UUID id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);
}
