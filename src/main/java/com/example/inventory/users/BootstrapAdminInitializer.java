package com.example.inventory.users;

import com.example.inventory.security.SecurityProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final SecurityProperties properties;
    private final UserProvisioningService provisioningService;

    public BootstrapAdminInitializer(SecurityProperties properties,
                                     UserProvisioningService provisioningService) {
        this.properties = properties;
        this.provisioningService = provisioningService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        SecurityProperties.BootstrapAdmin bootstrap = properties.bootstrapAdmin();
        if (bootstrap == null) {
            return;
        }
        boolean hasUsername = hasText(bootstrap.username());
        boolean hasEmail = hasText(bootstrap.email());
        boolean hasPassword = hasText(bootstrap.password());
        boolean complete = hasUsername && hasEmail && hasPassword;
        if ((hasUsername || hasEmail || hasPassword) && !complete) {
            throw new IllegalStateException("Bootstrap administrator configuration is incomplete");
        }
        if (!bootstrap.enabled()) {
            return;
        }
        if (!complete) {
            throw new IllegalStateException("Bootstrap administrator credentials must be provided externally");
        }
        provisioningService.createInitialAdmin(
                bootstrap.username(), bootstrap.email(), bootstrap.password());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
