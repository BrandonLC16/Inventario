package com.example.inventory.customers;

import com.example.inventory.shared.ConflictException;
import com.example.inventory.shared.NotFoundException;
import com.example.inventory.shared.PageResponse;
import com.example.inventory.shared.PageSupport;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
@Transactional(readOnly = true)
public class CustomerService implements CustomerDirectory {

    private final CustomerRepository customers;

    CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    public PageResponse<CustomerResponse> findAll(int page, int size, String search,
                                                   Boolean active) {
        var pageable = PageSupport.request(page, size,
                Sort.by(Sort.Direction.ASC, "name")
                        .and(Sort.by(Sort.Direction.ASC, "id")));
        return PageResponse.from(customers.findAll(filters(search, active), pageable),
                CustomerResponse::from);
    }

    public CustomerResponse findById(UUID id) {
        return CustomerResponse.from(requireCustomer(id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        String fiscalIdentifier = normalizeFiscalIdentifier(request.fiscalIdentifier());
        String email = normalizeEmail(request.email());
        ensureUnique(fiscalIdentifier, email, UUID.randomUUID());
        Customer customer = new Customer(
                request.name().trim(), fiscalIdentifier, email, request.active());
        return CustomerResponse.from(customers.save(customer));
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = requireCustomer(id);
        String fiscalIdentifier = normalizeFiscalIdentifier(request.fiscalIdentifier());
        String email = normalizeEmail(request.email());
        ensureUnique(fiscalIdentifier, email, id);
        customer.update(request.name().trim(), fiscalIdentifier, email, request.active());
        return CustomerResponse.from(customer);
    }

    @Transactional
    public void deactivate(UUID id) {
        requireCustomer(id).deactivate();
    }

    @Override
    public void requireActiveCustomer(UUID customerId) {
        Customer customer = requireCustomer(customerId);
        if (!customer.isActive()) {
            throw new ConflictException("Customer %s is inactive".formatted(customerId));
        }
    }

    private Customer requireCustomer(UUID id) {
        return customers.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Customer %s was not found".formatted(id)));
    }

    private void ensureUnique(String fiscalIdentifier, String email, UUID excludedId) {
        if (fiscalIdentifier != null
                && customers.existsByFiscalIdentifierAndIdNot(fiscalIdentifier, excludedId)) {
            throw new ConflictException("Fiscal identifier already exists");
        }
        if (email != null && customers.existsByEmailIgnoreCaseAndIdNot(email, excludedId)) {
            throw new ConflictException("Customer email already exists");
        }
    }

    private Specification<Customer> filters(String search, Boolean active) {
        String term = normalizeText(search);
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (term != null) {
                String pattern = "%" + term.toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("fiscalIdentifier")), pattern),
                        builder.like(builder.lower(root.get("email")), pattern)));
            }
            if (active != null) {
                predicates.add(builder.equal(root.get("active"), active));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String normalizeFiscalIdentifier(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
