package com.example.crudapi.controller;

import com.example.crudapi.dto.UserResponse;
import com.example.crudapi.dto.UserUpdateRequest;
import com.example.crudapi.dto.UserCreateRequest;
import com.example.crudapi.model.User;
import com.example.crudapi.repository.UserRepository;
import com.example.crudapi.security.CustomUserDetails;
import com.example.crudapi.service.UserService;

import lombok.RequiredArgsConstructor;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest newUser) {

        UserResponse saved = userService.createUser(newUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserResponse> getAllUsers(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "10") int size) {

        return userService.getAllUsers(PageRequest.of(page, size));
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(Authentication authentication) {
        CustomUserDetails cud = (CustomUserDetails) authentication.getPrincipal();
        return userService.getById(cud.getId());
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable String id,
                                   @Valid @RequestBody UserUpdateRequest req,
                                   Authentication authentication) {
        CustomUserDetails cud = (CustomUserDetails) authentication.getPrincipal();
        boolean isAdmin = cud.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return userService.updateUser(id, req, cud.getId(), isAdmin);
    }

    @PatchMapping("/me")
    public UserResponse updateSelf(@Valid @RequestBody UserUpdateRequest req,
                                   Authentication authentication) {
        CustomUserDetails cud = (CustomUserDetails) authentication.getPrincipal();
        return userService.updateUser(cud.getId(), req, cud.getId(), false);
    }

    // Allow admin to delete any user, and a user to delete themself
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id, Authentication authentication) {
        CustomUserDetails cud = (CustomUserDetails) authentication.getPrincipal();
        boolean isAdmin = cud.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        userService.deleteUser(id, cud.getId(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    // Delete own account
    @DeleteMapping("/me")
    public ResponseEntity<?> deleteSelf(Authentication authentication) {
        CustomUserDetails cud = (CustomUserDetails) authentication.getPrincipal();
        userService.deleteUser(cud.getId(), cud.getId(), false);
        return ResponseEntity.noContent().build();
    }
}