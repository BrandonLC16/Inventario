package com.example.inventory.customers;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 32) String fiscalIdentifier,
        @Email @Size(max = 254) String email,
        @NotNull Boolean active) {
}
