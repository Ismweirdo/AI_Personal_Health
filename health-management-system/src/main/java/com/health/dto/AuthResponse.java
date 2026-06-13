package com.health.dto;

public class AuthResponse {
    private String token;
    private String refreshToken;
    private Long userId;
    private String username;

    public AuthResponse() {}

    public AuthResponse(String token, String refreshToken, Long userId, String username) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.username = username;
    }

    public static AuthResponseBuilder builder() {
        return new AuthResponseBuilder();
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public static class AuthResponseBuilder {
        private String token;
        private String refreshToken;
        private Long userId;
        private String username;

        public AuthResponseBuilder token(String token) { this.token = token; return this; }
        public AuthResponseBuilder refreshToken(String refreshToken) { this.refreshToken = refreshToken; return this; }
        public AuthResponseBuilder userId(Long userId) { this.userId = userId; return this; }
        public AuthResponseBuilder username(String username) { this.username = username; return this; }
        public AuthResponse build() {
            return new AuthResponse(token, refreshToken, userId, username);
        }
    }
}
