package com.health.dto;

public class SmsCodeResponse {
    private String message;
    private Integer expiresInSeconds;
    private Integer resendAfterSeconds;
    private String debugCode;

    public SmsCodeResponse() {}

    public SmsCodeResponse(String message, Integer expiresInSeconds, Integer resendAfterSeconds, String debugCode) {
        this.message = message;
        this.expiresInSeconds = expiresInSeconds;
        this.resendAfterSeconds = resendAfterSeconds;
        this.debugCode = debugCode;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(Integer expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }
    public Integer getResendAfterSeconds() { return resendAfterSeconds; }
    public void setResendAfterSeconds(Integer resendAfterSeconds) { this.resendAfterSeconds = resendAfterSeconds; }
    public String getDebugCode() { return debugCode; }
    public void setDebugCode(String debugCode) { this.debugCode = debugCode; }
}
