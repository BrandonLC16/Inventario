package com.example.inventory.users;

import com.example.inventory.shared.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdministrationServiceTest {

    private UserAccountRepository users;
    private RoleRepository roles;
    private PasswordEncoder encoder;
    private UserAdministrationService service;

    @BeforeEach
    void setUp() {
        users = mock(UserAccountRepository.class);
        roles = mock(RoleRepository.class);
        when(roles.findByNameForUpdate(RoleName.ADMIN)).thenReturn(Optional.of(UserTestFixtures.role(RoleName.ADMIN)));
        encoder = mock(PasswordEncoder.class);
        service = new UserAdministrationService(users, roles, encoder);
    }

    @Test
    void createNormalizesIdentityHashesPasswordAndAssignsRoles() {
        Role sales = UserTestFixtures.role(RoleName.SALES);
        when(roles.findAllByNameIn(Set.of(RoleName.SALES))).thenReturn(Set.of(sales));
        when(encoder.encode("long-enough-password")).thenReturn("{bcrypt}hash");
        when(users.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = service.create(new CreateUserRequest(
                "  New.User  ", "  NEW.USER@EXAMPLE.COM  ", "long-enough-password",
                Set.of(RoleName.SALES)));

        assertEquals("new.user", response.username());
        assertEquals("new.user@example.com", response.email());
        assertEquals(Set.of(RoleName.SALES), response.roles());
        verify(encoder).encode("long-enough-password");
    }

    @Test
    void duplicateIdentityIsRejectedBeforeEncodingOrSaving() {
        when(users.existsByUsernameIgnoreCase("duplicate")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.create(new CreateUserRequest(
                "duplicate", "unique@example.com", "long-enough-password",
                Set.of(RoleName.SALES))));

        verify(encoder, never()).encode(any());
        verify(users, never()).save(any());
    }

    @Test
    void lastActiveAdminCannotBeDisabledLockedOrDemoted() {
        UserAccount admin = UserTestFixtures.user("admin", "admin@example.com", "{noop}hidden",
                true, false, RoleName.ADMIN);
        when(users.findByIdWithRoles(admin.getId())).thenReturn(Optional.of(admin));
        Role sales = UserTestFixtures.role(RoleName.SALES);
        when(roles.findAllByNameIn(Set.of(RoleName.SALES))).thenReturn(Set.of(sales));
        when(users.countActiveWithRole(RoleName.ADMIN)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> service.updateStatus(admin.getId(),
                new UpdateUserStatusRequest(false, false)));
        assertThrows(ConflictException.class, () -> service.updateStatus(admin.getId(),
                new UpdateUserStatusRequest(true, true)));
        assertThrows(ConflictException.class, () -> service.replaceRoles(admin.getId(),
                new UpdateUserRolesRequest(Set.of(RoleName.SALES))));
    }

    @Test
    void statusCanBeUpdatedWhenAnotherActiveAdminExists() {
        UserAccount admin = UserTestFixtures.user("admin", "admin@example.com", "{noop}hidden",
                true, false, RoleName.ADMIN);
        when(users.findByIdWithRoles(admin.getId())).thenReturn(Optional.of(admin));
        when(users.countActiveWithRole(RoleName.ADMIN)).thenReturn(2L);

        UserResponse response = service.updateStatus(admin.getId(),
                new UpdateUserStatusRequest(false, false));

        assertFalse(response.enabled());
    }
}
