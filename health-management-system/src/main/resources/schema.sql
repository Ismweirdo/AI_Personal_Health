-- 健康管理系统数据库初始化脚本
-- MySQL 8.0+

CREATE DATABASE IF NOT EXISTS health_management
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_unicode_ci;

USE health_management;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20) UNIQUE,
    avatar_url VARCHAR(255),
    membership_type VARCHAR(255) DEFAULT 'free',
    status INT NOT NULL DEFAULT 1,
    last_login_time DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS login_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    login_ip VARCHAR(255),
    login_type VARCHAR(255),
    success BOOLEAN,
    fail_reason VARCHAR(255),
    user_agent VARCHAR(255),
    login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_login_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS health_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    data_value DOUBLE NOT NULL,
    unit VARCHAR(20),
    notes VARCHAR(500),
    record_date DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_health_data_user_id (user_id),
    INDEX idx_health_data_type (type),
    INDEX idx_health_data_record_date (record_date),
    INDEX idx_health_data_user_type_date (user_id, type, record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS health_goals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    target_value DOUBLE NOT NULL,
    unit VARCHAR(20),
    period VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_health_goals_user_id (user_id),
    INDEX idx_health_goals_type (type),
    INDEX idx_health_goals_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reminder_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    type VARCHAR(50),
    message VARCHAR(500),
    frequency VARCHAR(20) NOT NULL,
    remind_time VARCHAR(10),
    remind_date DATE,
    weekly_day INT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    next_trigger_at DATETIME,
    last_triggered_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_reminder_rules_user_id (user_id),
    INDEX idx_reminder_rules_next_trigger_at (next_trigger_at),
    INDEX idx_reminder_rules_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS family_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    creator_user_id BIGINT NOT NULL,
    max_members INT NOT NULL DEFAULT 5,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_family_group_creator (creator_user_id),
    INDEX idx_family_group_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS family_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'child',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_family_user (family_id, user_id),
    INDEX idx_family_member_family (family_id),
    INDEX idx_family_member_user (user_id),
    INDEX idx_family_member_role (role),
    INDEX idx_family_member_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS family_invitations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    inviter_user_id BIGINT NOT NULL,
    invitee_phone VARCHAR(20) NOT NULL,
    invitee_role VARCHAR(20) NOT NULL,
    invite_code VARCHAR(20) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    expires_at DATETIME NOT NULL,
    accepted_by_user_id BIGINT,
    accepted_at DATETIME,
    approved_by_user_id BIGINT,
    approved_at DATETIME,
    rejected_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_family_invitation_family (family_id),
    INDEX idx_family_invitation_inviter (inviter_user_id),
    INDEX idx_family_invitation_phone (invitee_phone),
    INDEX idx_family_invitation_code (invite_code),
    INDEX idx_family_invitation_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    rule_id BIGINT,
    title VARCHAR(100) NOT NULL,
    type VARCHAR(50),
    message VARCHAR(500),
    action_type VARCHAR(50),
    action_ref_id BIGINT,
    action_status VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'unread',
    scheduled_for DATETIME,
    read_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notification_records_user_id (user_id),
    INDEX idx_notification_records_status (status),
    INDEX idx_notification_records_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS health_devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(100) NOT NULL UNIQUE,
    device_name VARCHAR(100) NOT NULL,
    device_type VARCHAR(50) NOT NULL,
    device_model VARCHAR(100),
    manufacturer VARCHAR(100),
    firmware_version VARCHAR(50),
    api_key VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    last_active DATETIME,
    description VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_health_devices_user_id (user_id),
    INDEX idx_health_devices_device_id (device_id),
    INDEX idx_health_devices_status (status),
    INDEX idx_health_devices_api_key (api_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS device_data_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    data_type VARCHAR(255) NOT NULL,
    data_value DOUBLE NOT NULL,
    unit VARCHAR(255),
    record_date DATETIME NOT NULL,
    source VARCHAR(255) NOT NULL,
    request_id VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    error_message VARCHAR(500),
    processing_time_ms BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device_data_logs_device_id (device_id),
    INDEX idx_device_data_logs_user_id (user_id),
    INDEX idx_device_data_logs_created_at (created_at),
    INDEX idx_device_data_logs_record_date (record_date),
    INDEX idx_device_data_logs_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chat_id VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    timestamp DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_chat_message_user_id (user_id),
    INDEX idx_ai_chat_message_chat_id (chat_id),
    INDEX idx_ai_chat_message_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS health_report_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    period VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    metrics_json LONGTEXT NOT NULL,
    goals_json LONGTEXT NOT NULL,
    highlights_json LONGTEXT NOT NULL,
    suggestions_json LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_health_report_snapshot_user_id (user_id),
    INDEX idx_health_report_snapshot_period (period),
    INDEX idx_health_report_snapshot_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE family_groups
    ADD COLUMN IF NOT EXISTS max_members INT NOT NULL DEFAULT 5;

ALTER TABLE family_invitations
    ADD COLUMN IF NOT EXISTS approved_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS approved_at DATETIME,
    ADD COLUMN IF NOT EXISTS rejected_at DATETIME;

ALTER TABLE notification_records
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS action_ref_id BIGINT,
    ADD COLUMN IF NOT EXISTS action_status VARCHAR(20);
