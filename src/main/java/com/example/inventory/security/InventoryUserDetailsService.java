package com.example.inventory.security;

import com.example.inventory.users.UserAccount;
import com.example.inventory.users.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class InventoryUserDetailsService implements UserDetailsService {

    private final UserAccountRepository repository;

    public InventoryUserDetailsService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        UserAccount account = repository.findByUsernameIgnoreCase(normalized)
                .or(() -> repository.findByEmailIgnoreCase(normalized))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return InventoryUserDetails.from(account);
    }
}
