package com.example.inventory.users;

import com.example.inventory.shared.ConflictException;
import com.example.inventory.shared.NotFoundException;
import com.example.inventory.security.SessionRevoker;
import com.example.inventory.shared.PageResponse;
import com.example.inventory.shared.PageSupport;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
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
    private final SessionRevoker sessions;

    public UserAdministrationService(UserAccountRepository users, RoleRepository roles,
                                     PasswordEncoder passwordEncoder,
                                     SessionRevoker sessions) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
    }

    public PageResponse<UserResponse> findAll(
            int page, int size, String username, String email, RoleName role,
            Boolean enabled, Boolean locked) {
        var pageable = PageSupport.request(page, size,
                Sort.by(Sort.Direction.ASC, "username")
                        .and(Sort.by(Sort.Direction.ASC, "id")));
        return PageResponse.from(users.findAll(
                filters(username, email, role, enabled, locked), pageable),
                UserResponse::from);
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
        UserAccount account = requireUserForUpdate(id);
        if (isActiveAdmin(account) && (!request.enabled() || request.locked())
                && users.countActiveWithRole(RoleName.ADMIN) <= 1) {
            throw new ConflictException("The last active administrator cannot be disabled or locked");
        }
        boolean changed = account.isEnabled() != request.enabled()
                || account.isLocked() != request.locked();
        account.updateStatus(request.enabled(), request.locked());
        if (changed) {
            sessions.revokeAll(account);
        }
        return UserResponse.from(account);
    }

    @Transactional
    public UserResponse replaceRoles(UUID id, UpdateUserRolesRequest request) {
        lockAdministratorChanges();
        UserAccount account = requireUserForUpdate(id);
        Set<Role> replacement = requireRoles(request.roles());
        if (isActiveAdmin(account) && !request.roles().contains(RoleName.ADMIN)
                && users.countActiveWithRole(RoleName.ADMIN) <= 1) {
            throw new ConflictException("The ADMIN role cannot be removed from the last active administrator");
        }
        Set<RoleName> currentRoles = account.getRoles().stream()
                .map(Role::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        account.replaceRoles(replacement);
        if (!currentRoles.equals(request.roles())) {
            sessions.revokeAll(account);
        }
        return UserResponse.from(account);
    }

    @Transactional
    public void resetPassword(UUID id, ResetPasswordRequest request) {
        UserAccount account = requireUserForUpdate(id);
        account.replacePasswordHash(passwordEncoder.encode(request.newPassword()));
        sessions.revokeAll(account);
    }

    @Transactional
    public void revokeSessions(UUID id) {
        sessions.revokeAll(requireUserForUpdate(id));
    }

    private void lockAdministratorChanges() {
        roles.findByNameForUpdate(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role is missing from the database"));
    }

    private UserAccount requireUser(UUID id) {
        return users.findByIdWithRoles(id)
                .orElseThrow(() -> new NotFoundException("User %s was not found".formatted(id)));
    }

    private UserAccount requireUserForUpdate(UUID id) {
        return users.findByIdForUpdate(id)
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

    private Specification<UserAccount> filters(
            String username, String email, RoleName role,
            Boolean enabled, Boolean locked) {
        String normalizedUsername = normalizeFilter(username);
        String normalizedEmail = normalizeFilter(email);
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (normalizedUsername != null) {
                predicates.add(builder.like(builder.lower(root.get("username")),
                        "%" + normalizedUsername + "%"));
            }
            if (normalizedEmail != null) {
                predicates.add(builder.like(builder.lower(root.get("email")),
                        "%" + normalizedEmail + "%"));
            }
            if (role != null) {
                predicates.add(builder.equal(
                        root.join("roles", JoinType.INNER).get("name"), role));
                query.distinct(true);
            }
            if (enabled != null) {
                predicates.add(builder.equal(root.get("enabled"), enabled));
            }
            if (locked != null) {
                predicates.add(builder.equal(root.get("locked"), locked));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank()
                ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
