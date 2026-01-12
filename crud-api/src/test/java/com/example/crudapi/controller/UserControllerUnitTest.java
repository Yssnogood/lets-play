package com.example.crudapi.controller;

import com.example.crudapi.dto.UserResponse;
import com.example.crudapi.model.User;
import com.example.crudapi.security.CustomUserDetails;
import com.example.crudapi.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;

public class UserControllerUnitTest {

    @Test
    public void getCurrentUser_shouldReturnUserResponse_forAuthenticatedPrincipal() {
        // Arrange: create a User and CustomUserDetails
        User u = new User();
        u.setId("507f1f77bcf86cd799439011");
        u.setName("Bob");
        u.setEmail("bob@example.com");
        u.setRole("ROLE_USER");

        CustomUserDetails cud = new CustomUserDetails(u);

        // stub UserService
        UserService stubService = new UserService(null, null, null) {
            @Override
            public UserResponse getById(String id) {
                // ensure id matches
                return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole());
            }
        };

        UserController controller = new UserController(stubService);

        // fake Authentication
        Authentication auth = new Authentication() {
            @Override
            public Object getPrincipal() { return cud; }
            // other methods not needed for this test
            @Override public Object getCredentials() { return null; }
            @Override public Object getDetails() { return null; }
            @Override public String getName() { return cud.getUsername(); }
            @Override public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() { return cud.getAuthorities(); }
            @Override public boolean isAuthenticated() { return true; }
            @Override public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException { }
        };

        // Act
        UserResponse resp = controller.getCurrentUser(auth);

        // Assert
        assertThat(resp).isNotNull();
        assertThat(resp.getId()).isEqualTo(u.getId());
        assertThat(resp.getEmail()).isEqualTo(u.getEmail());
        assertThat(resp.getName()).isEqualTo(u.getName());
        assertThat(resp.getRole()).isEqualTo(u.getRole());
    }
}
