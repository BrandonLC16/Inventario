package com.example.inventory.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Access and refresh token operations")
public class AuthController {

    private final AuthService service;
    private final AuthenticationRateLimiter rateLimiter;

    public AuthController(AuthService service, AuthenticationRateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with username or email")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest servletRequest) {
        String remoteAddress = servletRequest.getRemoteAddr();
        rateLimiter.checkLogin(remoteAddress, request.identifier());
        TokenResponse response = service.login(request);
        rateLimiter.loginSucceeded(remoteAddress, request.identifier());
        return tokenResponse(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                                 HttpServletRequest servletRequest) {
        String remoteAddress = servletRequest.getRemoteAddr();
        rateLimiter.checkRefresh(remoteAddress, request.refreshToken());
        TokenResponse response = service.refresh(request);
        rateLimiter.refreshSucceeded(remoteAddress, request.refreshToken());
        return tokenResponse(response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a refresh token family")
    public void logout(@Valid @RequestBody RefreshRequest request) {
        service.logout(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user")
    public CurrentUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return service.me(jwt.getSubject());
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change the authenticated user's password")
    public void changePassword(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(jwt.getSubject(), request);
    }

    private ResponseEntity<TokenResponse> tokenResponse(TokenResponse body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(body);
    }
}
