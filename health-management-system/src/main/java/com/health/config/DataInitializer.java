package com.health.config;

import com.health.entity.HealthData;
import com.health.entity.User;
import com.health.repository.HealthDataRepository;
import com.health.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;

@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final HealthDataRepository healthDataRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Random RANDOM = new SecureRandom();

    public DataInitializer(UserRepository userRepository,
                           HealthDataRepository healthDataRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.healthDataRepository = healthDataRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        log.info("=== 初始化测试数据 ===");

        // 1. 创建 testuser
        User testUser = userRepository.findByUsername("testuser").orElse(null);
        if (testUser == null) {
            testUser = new User();
            testUser.setUsername("testuser");
            testUser.setPassword(passwordEncoder.encode("Test123456"));
            testUser.setEmail("test@health.com");
            testUser.setStatus(1);
            testUser.setMembershipType("free");
            testUser.setCreatedAt(LocalDateTime.now());
            testUser.setUpdatedAt(LocalDateTime.now());
            testUser = userRepository.save(testUser);
            log.info("创建测试用户: testuser / Test123456 (ID={})", testUser.getId());
        } else {
            log.info("测试用户已存在 (ID={})", testUser.getId());
        }

        // 2. 检查是否需要录入健康数据
        long count = healthDataRepository.count();
        if (count > 0) {
            log.info("健康数据已存在 ({}条)，跳过初始化", count);
            return;
        }

        // 3. 录入30天健康数据
        Long userId = testUser.getId();
        String[] types = {"steps", "heart_rate", "sleep", "weight", "blood_pressure", "blood_sugar"};
        String[] units = {"步", "bpm", "小时", "kg", "mmHg", "mmol/L"};
        double[][] normalRanges = {{5000, 12000}, {62, 85}, {6.5, 8.5}, {68.5, 71.0}, {108, 128}, {4.2, 5.8}};
        int[] decimals = {0, 0, 1, 1, 0, 1};

        int totalCount = 0;
        LocalDate today = LocalDate.now();

        for (int day = 0; day < 30; day++) {
            LocalDate date = today.minusDays(29 - day);
            boolean isFeverDay = day >= 10 && day <= 12;
            boolean isSugarDay = day >= 22 && day <= 24;

            for (int i = 0; i < types.length; i++) {
                double[] range = normalRanges[i];
                double lo = range[0], hi = range[1];

                if (isFeverDay) {
                    if (types[i].equals("steps")) { lo = 800; hi = 3000; }
                    else if (types[i].equals("heart_rate")) { lo = 88; hi = 110; }
                    else if (types[i].equals("sleep")) { lo = 4.0; hi = 6.0; }
                    else if (types[i].equals("blood_pressure")) { lo = 130; hi = 155; }
                }
                if (isSugarDay && types[i].equals("blood_sugar")) { lo = 7.2; hi = 9.5; }

                double value = lo + RANDOM.nextDouble() * (hi - lo);
                int dec = decimals[i];
                if (dec == 0) value = Math.round(value);
                else value = Math.round(value * Math.pow(10, dec)) / Math.pow(10, dec);

                HealthData hd = new HealthData();
                hd.setUserId(userId);
                hd.setType(types[i]);
                hd.setDataValue(value);
                hd.setUnit(units[i]);
                hd.setRecordDate(date.atStartOfDay());
                healthDataRepository.save(hd);
                totalCount++;
            }
        }

        log.info("录入30天健康数据完成: {}条 (含第11-13天发烧期、第23-25天血糖异常期)", totalCount);
    }
}
