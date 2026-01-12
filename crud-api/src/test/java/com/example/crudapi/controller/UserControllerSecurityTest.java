package com.example.crudapi.controller;

import com.example.crudapi.dto.UserCreateRequest;
import com.example.crudapi.dto.UserResponse;
import com.example.crudapi.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void postUsers_allowedForAdmin() throws Exception {
        UserCreateRequest req = new UserCreateRequest();
        req.setName("AdminCreated");
        req.setEmail("a@ex.com");
        req.setPassword("pass");

        when(userService.createUser(any(UserCreateRequest.class))).thenReturn(new UserResponse("id","AdminCreated","a@ex.com","ROLE_ADMIN"));

        mockMvc.perform(post("/api/users")
                .contentType("application/json")
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = {"USER"})
    public void postUsers_forbiddenForNonAdmin() throws Exception {
        UserCreateRequest req = new UserCreateRequest();
        req.setName("UserCreated");
        req.setEmail("u@ex.com");
        req.setPassword("pass");

        mockMvc.perform(post("/api/users")
                .contentType("application/json")
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void getAllUsers_allowedForAdmin() throws Exception {
        when(userService.getAllUsers(any())).thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"USER"})
    public void getAllUsers_forbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }
}
