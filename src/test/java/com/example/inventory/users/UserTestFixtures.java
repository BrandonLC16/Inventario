package com.example.inventory.users;

import java.lang.reflect.Field;
import java.util.Arrays;

public final class UserTestFixtures {

    private UserTestFixtures() {
    }

    public static UserAccount user(String username, String email, String passwordHash,
                                   boolean enabled, boolean locked, RoleName... roles) {
        UserAccount account = new UserAccount(username, email, passwordHash, enabled, locked);
        Arrays.stream(roles).map(UserTestFixtures::role).forEach(account::addRole);
        return account;
    }

    public static Role role(RoleName name) {
        Role role = new Role();
        set(role, "name", name);
        set(role, "id", java.util.UUID.randomUUID());
        return role;
    }

    public static void addRole(UserAccount account, Role role) {
        account.addRole(role);
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot prepare test fixture", exception);
        }
    }
}
