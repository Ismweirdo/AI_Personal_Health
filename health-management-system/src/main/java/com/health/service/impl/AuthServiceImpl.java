package com.health.service.impl;

import com.health.common.ErrorCode;
import com.health.dto.AuthResponse;
import com.health.dto.LoginRequest;
import com.health.dto.PhoneCodeLoginRequest;
import com.health.dto.RegisterRequest;
import com.health.entity.LoginLog;
import com.health.entity.User;
import com.health.exception.BusinessException;
import com.health.repository.LoginLogRepository;
import com.health.repository.UserRepository;
import com.health.service.AuthService;
import com.health.service.SmsService;
import com.health.utils.JwtUtils;
import com.health.utils.CacheUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final LoginLogRepository loginLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final CacheUtils redisUtils;
    private final SmsService smsService;

    private static final String LOGIN_FAIL_PREFIX = "login:fail:";
    private static final String REGISTER_LOCK_PREFIX = "register:lock:";
    private static final String RATE_LIMIT_PREFIX = "rate:limit:";
    private static final int MAX_LOGIN_FAIL = 5;
    private static final int LOCK_TIME = 30; // minutes

    public AuthServiceImpl(UserRepository userRepository, 
                           LoginLogRepository loginLogRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtils jwtUtils,
                           CacheUtils redisUtils,
                           SmsService smsService) {
        this.userRepository = userRepository;
        this.loginLogRepository = loginLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.redisUtils = redisUtils;
        this.smsService = smsService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getUsername() != null) {
            request.setUsername(request.getUsername().trim());
        }
        if (request.getEmail() != null) {
            request.setEmail(request.getEmail().trim());
        }
        if (request.getPhone() != null) {
            request.setPhone(request.getPhone().trim());
        }

        String registerLockKey = REGISTER_LOCK_PREFIX + request.getEmail();
        
        // 0. Anti-duplicate submission & Rate limiting
        if (Boolean.TRUE.equals(redisUtils.get(registerLockKey) != null)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Registration in progress, please wait");
        }
        redisUtils.set(registerLockKey, "lock", 10, TimeUnit.SECONDS);

        try {
            // 1. Check uniqueness
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
            }
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            if (userRepository.existsByPhone(request.getPhone())) {
                throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
            }
            smsService.verifyCode(request.getPhone(), "register", request.getSmsCode());

            // 2. Create user
            User user = new User();
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());
            user.setPhone(request.getPhone());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setStatus(1); // Active
            userRepository.save(user);

            // 3. Generate token
            String token = jwtUtils.generateToken(user.getId(), user.getUsername());
            return AuthResponse.builder()
                    .token(token)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .build();
        } finally {
            redisUtils.delete(registerLockKey);
        }
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        if (request.getAccount() != null) {
            request.setAccount(request.getAccount().trim());
        }
        String account = request.getAccount();
        String lockKey = LOGIN_FAIL_PREFIX + account;

        // 1. Check lock
        String failCountStr = redisUtils.get(lockKey);
        if (failCountStr != null && Integer.parseInt(failCountStr) >= MAX_LOGIN_FAIL) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        // 2. Find user
        User user = userRepository.findByUsername(account)
                .or(() -> userRepository.findByEmail(account))
                .or(() -> userRepository.findByPhone(account))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 3. Check status
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        // 4. Check password
        boolean match = passwordEncoder.matches(request.getPassword(), user.getPassword());
        
        // 5. Log login
        LoginLog logEntry = new LoginLog();
        logEntry.setUserId(user.getId());
        logEntry.setLoginType(determineLoginType(account));
        logEntry.setSuccess(match);
        
        if (!match) {
            logEntry.setFailReason("Invalid password");
            loginLogRepository.save(logEntry);
            
            // Increment fail count
            Long count = redisUtils.increment(lockKey);
            if (count == 1) {
                redisUtils.expire(lockKey, LOCK_TIME, TimeUnit.MINUTES);
            }
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        // 6. Success: Clear fail count, update user, generate token
        redisUtils.delete(lockKey);
        user.setLastLoginTime(LocalDateTime.now());
        userRepository.save(user);
        
        loginLogRepository.save(logEntry);

        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    @Override
    @Transactional
    public AuthResponse loginWithPhoneCode(PhoneCodeLoginRequest request) {
        String phone = request.getPhone() == null ? "" : request.getPhone().trim();
        smsService.verifyCode(phone, "login", request.getSmsCode());

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        user.setLastLoginTime(LocalDateTime.now());
        userRepository.save(user);

        LoginLog logEntry = new LoginLog();
        logEntry.setUserId(user.getId());
        logEntry.setLoginType("PHONE_CODE");
        logEntry.setSuccess(true);
        loginLogRepository.save(logEntry);

        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    private String determineLoginType(String account) {
        if (account.contains("@")) return "EMAIL";
        if (account.matches("^1[3-9]\\d{9}$")) return "PHONE";
        return "USERNAME";
    }

    @Override
    public void logout() {
        String token = jwtUtils.getCurrentToken();
        if (token != null) {
            // Add token to blacklist
            String blacklistKey = "token:blacklist:" + token;
            redisUtils.set(blacklistKey, "1", jwtUtils.getTokenExpiration(), TimeUnit.MILLISECONDS);
        }
    }
}
