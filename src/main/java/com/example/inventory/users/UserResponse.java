package com.example.inventory.users;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserResponse(
        UUID id,
        String username,
        String email,
        boolean enabled,
        boolean locked,
        Set<RoleName> roles,
        Instant createdAt,
        Instant updatedAt) {

    public UserResponse {
        roles = Set.copyOf(roles);
    }

    static UserResponse from(UserAccount account) {
        return new UserResponse(account.getId(), account.getUsername(), account.getEmail(),
                account.isEnabled(), account.isLocked(),
                account.getRoles().stream().map(Role::getName)
                        .collect(Collectors.toUnmodifiableSet()),
                account.getCreatedAt(), account.getUpdatedAt());
    }
}
