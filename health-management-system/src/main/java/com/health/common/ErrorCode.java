package com.health.common;

public enum ErrorCode {
    SUCCESS(200, "Success"),
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),

    // User Errors
    USER_ALREADY_EXISTS(1001, "User already exists"),
    USER_NOT_FOUND(1002, "User not found"),
    INVALID_PASSWORD(1003, "Invalid password"),
    ACCOUNT_LOCKED(1004, "Account is locked, please try again later"),
    PASSWORD_STRENGTH_NOT_ENOUGH(1005, "Password must be at least 8 characters long and contain uppercase, lowercase letters, numbers, and special characters"),
    EMAIL_ALREADY_EXISTS(1006, "Email already exists"),
    PHONE_ALREADY_EXISTS(1007, "Phone already exists"),
    INVALID_VERIFICATION_CODE(1008, "Invalid or expired verification code"),
    EMAIL_FORMAT_ERROR(1009, "Invalid email format"),
    PHONE_FORMAT_ERROR(1010, "Invalid phone format"),

    // Rate Limit Errors
    RATE_LIMITED(2001, "操作过于频繁，请稍后重试");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
