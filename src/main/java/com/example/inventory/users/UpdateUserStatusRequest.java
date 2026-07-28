package com.example.inventory.users;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull Boolean enabled,
        @NotNull Boolean locked) {
}
