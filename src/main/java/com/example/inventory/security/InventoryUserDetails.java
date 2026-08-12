package com.example.inventory.security;

import com.example.inventory.users.RoleName;
import com.example.inventory.users.UserAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class InventoryUserDetails implements UserDetails {

    private final UUID id;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final long accessTokenVersion;
    private final Set<RoleName> roles;
    private final List<GrantedAuthority> authorities;

    private InventoryUserDetails(UserAccount account) {
        this.id = account.getId();
        this.username = account.getUsername();
        this.email = account.getEmail();
        this.passwordHash = account.getPasswordHash();
        this.enabled = account.isEnabled();
        this.accountNonLocked = !account.isLocked();
        this.accessTokenVersion = account.getAccessTokenVersion();
        this.roles = account.getRoles().stream()
                .map(role -> role.getName())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.authorities = roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    public static InventoryUserDetails from(UserAccount account) {
        return new InventoryUserDetails(account);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public Set<RoleName> roles() {
        return roles;
    }

    public long accessTokenVersion() {
        return accessTokenVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }
}
