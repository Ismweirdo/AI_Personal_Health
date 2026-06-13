package com.health.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.dto.AuthResponse;
import com.health.dto.RegisterRequest;
import com.health.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void register_shouldNotReturn403_whenNoTokenProvided() throws Exception {
        AuthResponse response = AuthResponse.builder()
                .token("token")
                .userId(1L)
                .username("tester")
                .build();
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("tester");
        request.setEmail("tester@example.com");
        request.setPassword("1");
        request.setPhone("13800138000");
        request.setSmsCode("123456");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("tester"));
    }

    @Test
    void register_shouldRejectEmptyPhone_whenPhoneIsRequired() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("tester");
        request.setEmail("tester2@example.com");
        request.setPassword("1");
        request.setPhone("");
        request.setSmsCode("123456");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void register_shouldRejectTooLongUsername() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("a".repeat(51));
        request.setEmail("tester3@example.com");
        request.setPassword("1");
        request.setPhone("13800138002");
        request.setSmsCode("123456");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名长度不能超过50"));
    }

    @Test
    void corsPreflight_shouldPass_forRegisterEndpoint() throws Exception {
        mockMvc.perform(options("/auth/register")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
