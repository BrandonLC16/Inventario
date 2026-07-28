package com.example.inventory.auth;

public class InvalidAuthenticationException extends RuntimeException {

    public InvalidAuthenticationException() {
        super("Authentication failed");
    }
}
