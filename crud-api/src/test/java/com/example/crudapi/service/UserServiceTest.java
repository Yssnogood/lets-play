package com.example.crudapi.service;

import com.example.crudapi.dto.UserResponse;
import com.example.crudapi.dto.UserUpdateRequest;
import com.example.crudapi.model.User;
import com.example.crudapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    public void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        // JwtUtil not required for updateUser tests
        userService = new UserService(userRepository, passwordEncoder, null);
    }

    @Test
    public void updateUser_shouldThrowNotFound_forNonExistingUser() {
        when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

        UserUpdateRequest req = new UserUpdateRequest();
        req.setName("NewName");

        assertThatThrownBy(() -> userService.updateUser("nonexistent", req, "requesterId", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    public void updateUser_shouldThrowForbidden_whenRequesterNotAdminAndNotOwner() {
        User existing = new User();
        existing.setId("userA");
        existing.setName("Alice");
        existing.setEmail("alice@example.com");
        existing.setPassword("hashed");
        existing.setRole("ROLE_USER");

        when(userRepository.findById("userA")).thenReturn(Optional.of(existing));

        UserUpdateRequest req = new UserUpdateRequest();
        req.setName("HackerName");

        assertThatThrownBy(() -> userService.updateUser("userA", req, "otherUser", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not allowed to modify this user");
    }

    @Test
    public void updateUser_shouldUpdateNameAndEncodePassword_whenOwnerRequests() {
        User existing = new User();
        existing.setId("userB");
        existing.setName("Bob");
        existing.setEmail("bob@example.com");
        existing.setPassword("oldhashed");
        existing.setRole("ROLE_USER");

        when(userRepository.findById("userB")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("newpass")).thenReturn("newhashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserUpdateRequest req = new UserUpdateRequest();
        req.setName("Bobby");
        req.setPassword("newpass");

        UserResponse resp = userService.updateUser("userB", req, "userB", false);

        assertThat(resp.getName()).isEqualTo("Bobby");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPassword()).isEqualTo("newhashed");
    }
}
