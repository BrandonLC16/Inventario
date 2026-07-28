package com.example.inventory.users;

import com.example.inventory.shared.ConflictException;
import com.example.inventory.shared.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class UserAdministrationService {

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public UserAdministrationService(UserAccountRepository users, RoleRepository roles,
                                     PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> findAll() {
        return users.findAllByOrderByUsernameAsc().stream().map(UserResponse::from).toList();
    }

    public UserResponse findById(UUID id) {
        return UserResponse.from(requireUser(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        ensureIdentityAvailable(username, email);
        Set<Role> assignedRoles = requireRoles(request.roles());
        UserAccount account = new UserAccount(username, email,
                passwordEncoder.encode(request.password()), true, false);
        account.replaceRoles(assignedRoles);
        return UserResponse.from(users.save(account));
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UpdateUserStatusRequest request) {
        lockAdministratorChanges();
        UserAccount account = requireUser(id);
        if (isActiveAdmin(account) && (!request.enabled() || request.locked())
                && users.countActiveWithRole(RoleName.ADMIN) <= 1) {
            throw new ConflictException("The last active administrator cannot be disabled or locked");
        }
        account.updateStatus(request.enabled(), request.locked());
        return UserResponse.from(account);
    }

    @Transactional
    public UserResponse replaceRoles(UUID id, UpdateUserRolesRequest request) {
        lockAdministratorChanges();
        UserAccount account = requireUser(id);
        Set<Role> replacement = requireRoles(request.roles());
        if (isActiveAdmin(account) && !request.roles().contains(RoleName.ADMIN)
                && users.countActiveWithRole(RoleName.ADMIN) <= 1) {
            throw new ConflictException("The ADMIN role cannot be removed from the last active administrator");
        }
        account.replaceRoles(replacement);
        return UserResponse.from(account);
    }

    private void lockAdministratorChanges() {
        roles.findByNameForUpdate(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role is missing from the database"));
    }

    private UserAccount requireUser(UUID id) {
        return users.findByIdWithRoles(id)
                .orElseThrow(() -> new NotFoundException("User %s was not found".formatted(id)));
    }

    private Set<Role> requireRoles(Set<RoleName> names) {
        Set<Role> found = roles.findAllByNameIn(Set.copyOf(names));
        if (found.size() != names.size()) {
            throw new IllegalStateException("A configured role is missing from the database");
        }
        return found;
    }

    private void ensureIdentityAvailable(String username, String email) {
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Username already exists");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already exists");
        }
    }

    private boolean isActiveAdmin(UserAccount account) {
        return account.isEnabled() && !account.isLocked()
                && account.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ADMIN);
    }
}
