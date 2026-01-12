package com.example.crudapi.dto;

import com.example.crudapi.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UserResponseSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void userResponse_shouldNotContainPassword_whenSerialized() throws Exception {
        // Arrange: create a User with a password
        User u = new User();
        u.setId("507f1f77bcf86cd799439011");
        u.setName("Alice");
        u.setEmail("alice@example.com");
        u.setPassword("secret");
        u.setRole("ROLE_USER");

        // Act: build the DTO and serialize
        UserResponse dto = UserResponse.fromUser(u);
        String json = mapper.writeValueAsString(dto);

        // Assert: json contains id/name/email/role but not password
        assertThat(json).contains("\"id\"");
        assertThat(json).contains("\"name\"");
        assertThat(json).contains("\"email\"");
        assertThat(json).contains("\"role\"");
        assertThat(json).doesNotContain("password");
    }
}
