package com.example.crudapi.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

public class JwtUtilTest {

    @Test
    public void generateAndValidateToken() throws Exception {
        JwtUtil jwtUtil = new JwtUtil();

        // inject secret and expiration via reflection
        Field secretField = JwtUtil.class.getDeclaredField("SECRET");
        secretField.setAccessible(true);
        secretField.set(jwtUtil, "testsecretkeytestsecretkeytest1234");

        Field expField = JwtUtil.class.getDeclaredField("expirationMs");
        expField.setAccessible(true);
        expField.setLong(jwtUtil, 3600000L);

        UserDetails userDetails = User.withUsername("alice@example.com")
                .password("ignored")
                .roles("USER")
                .build();

        String token = jwtUtil.generateToken(userDetails);
        assertNotNull(token);

        String username = jwtUtil.extractUsername(token);
        assertEquals("alice@example.com", username);

        // extractUserId should be null for a plain User without CustomUserDetails
        String userId = jwtUtil.extractUserId(token);
        assertNull(userId);

        assertTrue(jwtUtil.validateToken(token, userDetails));
    }
}
