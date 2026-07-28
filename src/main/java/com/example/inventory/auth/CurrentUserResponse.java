package com.example.inventory.auth;

import com.example.inventory.users.RoleName;

import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String username,
        String email,
        Set<RoleName> roles) {
}
