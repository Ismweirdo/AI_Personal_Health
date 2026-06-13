package com.health.service;

import com.health.dto.AuthResponse;
import com.health.dto.LoginRequest;
import com.health.dto.RegisterRequest;
import com.health.entity.User;
import com.health.exception.BusinessException;
import com.health.repository.LoginLogRepository;
import com.health.repository.UserRepository;
import com.health.service.SmsService;
import com.health.service.impl.AuthServiceImpl;
import com.health.utils.JwtUtils;
import com.health.utils.CacheUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private LoginLogRepository loginLogRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private CacheUtils redisUtils;
    @Mock
    private SmsService smsService;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User user;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setPassword("Test@1234");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPhone("13800138000");
        registerRequest.setSmsCode("123456");

        loginRequest = new LoginRequest();
        loginRequest.setAccount("testuser");
        loginRequest.setPassword("Test@1234");

        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("encodedPassword");
        user.setStatus(1);
    }

    @Test
    void register_Success() {
        when(redisUtils.get(anyString())).thenReturn(null);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(jwtUtils.generateToken(any(), anyString())).thenReturn("token");

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        verify(userRepository, times(1)).save(any());
        verify(smsService, times(1)).verifyCode("13800138000", "register", "123456");
        verify(redisUtils, times(1)).set(anyString(), anyString(), anyLong(), any());
        verify(redisUtils, times(1)).delete(anyString());
    }

    @Test
    void register_UserExists() {
        when(redisUtils.get(anyString())).thenReturn(null);
        when(userRepository.existsByUsername(anyString())).thenReturn(true);
        assertThrows(BusinessException.class, () -> authService.register(registerRequest));
        verify(redisUtils, times(1)).delete(anyString());
    }

    @Test
    void register_ShouldTrimRequiredPhone() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(" testuser ");
        req.setPassword("Test@1234");
        req.setEmail(" test-normalize@example.com ");
        req.setPhone(" 13800138001 ");
        req.setSmsCode("654321");

        when(redisUtils.get(anyString())).thenReturn(null);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(jwtUtils.generateToken(any(), anyString())).thenReturn("token");

        authService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        assertEquals("13800138001", captor.getValue().getPhone());
        assertEquals("testuser", captor.getValue().getUsername());
        assertEquals("test-normalize@example.com", captor.getValue().getEmail());
        verify(smsService, times(1)).verifyCode("13800138001", "register", "654321");
    }

    @Test
    void login_Success() {
        when(redisUtils.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtUtils.generateToken(any(), anyString())).thenReturn("token");

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void login_InvalidPassword() {
        when(redisUtils.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(BusinessException.class, () -> authService.login(loginRequest));
        verify(redisUtils, times(1)).increment(anyString());
    }
}
