package com.example.inventory.suppliers;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SupplierRepository extends JpaRepository<Supplier, UUID>,
        JpaSpecificationExecutor<Supplier> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    boolean existsByFiscalIdentifier(String fiscalIdentifier);

    boolean existsByFiscalIdentifierAndIdNot(String fiscalIdentifier, UUID id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select supplier from Supplier supplier where supplier.id = :id")
    Optional<Supplier> findByIdForUpdate(@Param("id") UUID id);
}
