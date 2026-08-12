package com.example.inventory.users;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.inventory.shared.PageResponse;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Administrator-only user management")
public class UserController {

    private final UserAdministrationService service;

    public UserController(UserAdministrationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a user")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "List users")
    public PageResponse<UserResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean locked) {
        return service.findAll(page, size, username, email, role, enabled, locked);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user")
    public UserResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update enabled and locked status")
    public UserResponse updateStatus(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateUserStatusRequest request) {
        return service.updateStatus(id, request);
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Replace assigned roles")
    public UserResponse replaceRoles(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateUserRolesRequest request) {
        return service.replaceRoles(id, request);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Administratively reset a user's password")
    public void resetPassword(@PathVariable UUID id,
                              @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(id, request);
    }

    @PostMapping("/{id}/sessions/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke all sessions for a user")
    public void revokeSessions(@PathVariable UUID id) {
        service.revokeSessions(id);
    }
}
