package com.example.inventory.suppliers;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @Schema(example = "SUP-001")
        @NotBlank @Size(max = 32) String code,
        @Schema(example = "Distribuciones Ejemplo, S.A. de C.V.")
        @NotBlank @Size(max = 160) String legalName,
        @Schema(example = "Distribuciones Ejemplo")
        @Size(max = 160) String commercialName,
        @Schema(example = "DEX010101AB1")
        @Size(max = 32) String fiscalIdentifier,
        @Schema(example = "compras@distribuciones.example")
        @Email @Size(max = 254) String email,
        @Schema(example = "+52 55 5555 5555")
        @Size(max = 32) String phone,
        @NotNull Boolean active) {
}
