package com.health.dto;

import java.util.List;

public class CaptchaChallengeResponse {
    private String token;
    private String prompt;
    private List<Option> options;
    private Integer expiresInSeconds;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public List<Option> getOptions() { return options; }
    public void setOptions(List<Option> options) { this.options = options; }
    public Integer getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(Integer expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }

    public static class Option {
        private String id;
        private String imageData;
        private String alt;

        public Option() {}

        public Option(String id, String imageData, String alt) {
            this.id = id;
            this.imageData = imageData;
            this.alt = alt;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getImageData() { return imageData; }
        public void setImageData(String imageData) { this.imageData = imageData; }
        public String getAlt() { return alt; }
        public void setAlt(String alt) { this.alt = alt; }
    }
}
