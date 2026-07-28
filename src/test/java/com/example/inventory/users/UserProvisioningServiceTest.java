package com.example.inventory.users;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProvisioningServiceTest {

    @Test
    void bootstrapRejectsWeakAdministratorPasswordBeforeEncodingOrSaving() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(users.count()).thenReturn(0L);
        UserProvisioningService service = new UserProvisioningService(users, roles, encoder);

        assertThrows(IllegalStateException.class, () ->
                service.createInitialAdmin("admin", "admin@example.com", "short"));

        verify(encoder, never()).encode(any());
        verify(users, never()).save(any());
    }

    @Test
    void securityPropertiesRejectWeakOperationalConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new com.example.inventory.security.SecurityProperties(
                new com.example.inventory.security.SecurityProperties.Jwt("issuer", "audience",
                        java.time.Duration.ofHours(2), "", ""), java.time.Duration.ofDays(7),
                new com.example.inventory.security.SecurityProperties.Cors(java.util.List.of("*")), false,
                new com.example.inventory.security.SecurityProperties.BootstrapAdmin(false, "", "", "")));
    }
}
