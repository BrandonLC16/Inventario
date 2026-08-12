package com.example.inventory.security;

import com.example.inventory.users.UserAccount;

public interface SessionRevoker {

    void revokeAll(UserAccount user);
}
