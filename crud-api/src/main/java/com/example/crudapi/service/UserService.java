package com.example.crudapi.service;

import com.example.crudapi.dto.UserResponse;
import com.example.crudapi.model.User;
import com.example.crudapi.repository.UserRepository;
import com.example.crudapi.dto.UserUpdateRequest;
import com.example.crudapi.dto.UserCreateRequest;
import com.example.crudapi.security.JwtUtil;
import com.example.crudapi.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ProductRepository productRepository;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, ProductRepository productRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.productRepository = productRepository;
    }

    // Backwards-compatible constructor used by some unit tests/mocks
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this(userRepository, passwordEncoder, jwtUtil, null);
    }

    public UserResponse createUser(UserCreateRequest newUser) {
        User u = new User();
        u.setName(newUser.getName());
        u.setEmail(newUser.getEmail());
        u.setPassword(passwordEncoder.encode(newUser.getPassword()));
        if (newUser.getRole() != null) {
            u.setRole(newUser.getRole());
        } else {
            u.setRole("ROLE_USER");
        }
        User saved = userRepository.save(u);
        return UserResponse.fromUser(saved);
    }

    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::fromUser);
    }

    public UserResponse getById(String id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserResponse.fromUser(u);
    }

    public UserResponse updateUser(String id, UserUpdateRequest req, String requesterId, boolean requesterIsAdmin) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Only admin or the user themself can update
        if (!requesterIsAdmin && !requesterId.equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to modify this user");
        }

        if (req.getName() != null && !req.getName().isBlank()) {
            u.setName(req.getName());
        }

        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            u.setPassword(passwordEncoder.encode(req.getPassword()));
        }

        User saved = userRepository.save(u);
        return UserResponse.fromUser(saved);
    }

    // Delete a user and their products. Only admin or the user themself can delete.
    public void deleteUser(String id, String requesterId, boolean requesterIsAdmin) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!requesterIsAdmin && !requesterId.equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to delete this user");
        }

        // delete products owned by user
        if (productRepository != null) {
            productRepository.deleteByUserId(id);
        }

        // delete user
        userRepository.deleteById(id);
    }

    // Optional: helper to extract id from token
    public String extractUserIdFromToken(String token) {
        return jwtUtil.extractUserId(token);
    }
}
