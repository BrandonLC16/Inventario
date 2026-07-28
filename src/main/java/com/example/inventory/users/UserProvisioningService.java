package com.example.inventory.users;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserProvisioningService {

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public UserProvisioningService(UserAccountRepository users, RoleRepository roles,
                                   PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void createInitialAdmin(String username, String email, String rawPassword) {
        if (users.count() != 0) {
            return;
        }
        String normalizedUsername = requireText(username, "bootstrap admin username").toLowerCase(Locale.ROOT);
        String normalizedEmail = requireText(email, "bootstrap admin email").toLowerCase(Locale.ROOT);
        String password = requireText(rawPassword, "bootstrap admin password");
        if (password.length() < 12 || password.length() > 128) {
            throw new IllegalStateException("bootstrap admin password must contain between 12 and 128 characters");
        }
        if (normalizedUsername.contains("@")) {
            throw new IllegalStateException("bootstrap admin username must not contain @");
        }
        if (users.existsByUsernameIgnoreCase(normalizedUsername)
                || users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalStateException("Bootstrap administrator identity already belongs to another user");
        }
        Role adminRole = roles.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role is missing"));
        UserAccount admin = new UserAccount(normalizedUsername, normalizedEmail,
                passwordEncoder.encode(password), true, false);
        admin.addRole(adminRole);
        users.save(admin);
    }

    private static String requireText(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(description + " must be provided externally");
        }
        return value.trim();
    }
}
