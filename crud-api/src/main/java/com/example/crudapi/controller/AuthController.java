package com.example.crudapi.controller;

import com.example.crudapi.dto.LoginRequest;
import com.example.crudapi.dto.LoginResponse;
import com.example.crudapi.dto.UserResponse;
import com.example.crudapi.dto.UserCreateRequest;
import com.example.crudapi.repository.UserRepository;
import com.example.crudapi.security.JwtUtil;
import com.example.crudapi.security.CustomUserDetails;
import com.example.crudapi.service.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DuplicateKeyException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final JwtUtil jwtUtil;
	private final UserService userService;

	public AuthController(AuthenticationManager authenticationManager,
				  JwtUtil jwtUtil,
				  UserService userService) {

		this.authenticationManager = authenticationManager;
		this.jwtUtil = jwtUtil;
		this.userService = userService;
	}

	@PostMapping("/login")
	public LoginResponse login(@RequestBody LoginRequest request) {

		Authentication authentication =
				authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(
						request.getEmail(),
						request.getPassword()
					)
				);

		CustomUserDetails userDetails =
				(CustomUserDetails) authentication.getPrincipal();

		String token = jwtUtil.generateToken(userDetails);

		UserResponse user = userService.getById(userDetails.getId());

		return new LoginResponse(token, user);
	}

	@PostMapping("/register")
	public ResponseEntity<?> register(@Valid @RequestBody UserCreateRequest newUser) {

		UserResponse resp = userService.createUser(newUser);

		return ResponseEntity.status(HttpStatus.CREATED).body(resp);
	}
}